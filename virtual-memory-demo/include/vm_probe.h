// =============================================================================
// vm_probe.h · 跨平台进程/系统内存观测层（vm04 / vm06 共用）
// =============================================================================
// 职责：把 Linux(/proc) 与 macOS(libproc 的 proc_taskinfo) 两套观测通道
//       收敛成同一套 API，让实验正文只关心"观测什么"，不关心"在哪读"。
//
// ⚠️ 血泪注脚（真实踩过的坑，特意留档）：
//   macOS 的 rusage_info_v4 里【没有】minflt/majflt/cow_faults 字段——
//   那三个字段属于 getrusage() 的 struct rusage。macOS 上正确的一站式来源是
//   proc_pidinfo(PROC_PIDTASKINFO) 的 struct proc_taskinfo（sys/proc_info.h）：
//     pti_virtual_size / pti_resident_size / pti_faults / pti_pageins / pti_cow_faults
//   （top / Activity Monitor 同款数据源）
// =============================================================================
#pragma once

#include <cstdint>
#include <cstdlib>
#include <unistd.h>

#if defined(__linux__)
#  include <fstream>
#  include <sstream>
#  include <string>
#  include <vector>
#elif defined(__APPLE__)
#  include <libproc.h>     // proc_pidinfo / proc_taskinfo
#  include <mach/mach.h>  // host_statistics64（全系统空闲内存）
#else
#  error "vm_probe.h 目前只支持 Linux 与 macOS 两个观测通道"
#endif

namespace vm_probe {

// ── 统一内存快照（单位字节；缺页计数为绝对次数）─────────────────────────────
struct MemSnap {
    long     vsize;    // 虚拟内存（花名册登记量）
    long     rss;      // 驻留内存（真实占用量）
    uint64_t minflt;   // 轻量缺页总数（page faults）
    uint64_t majflt;   // 重量缺页总数（磁盘调入：majflt / pageins）
};

inline long page_size() { return sysconf(_SC_PAGESIZE); }

#if defined(__linux__)
// ── Linux 通道：/proc/self/status · /proc/self/stat · /proc/self/smaps · /proc/meminfo
inline long read_status_kb(const char* key) {
    std::ifstream f("/proc/self/status");
    std::string line;
    while (std::getline(f, line))
        if (line.rfind(key, 0) == 0) {
            size_t p = line.find_first_of("0123456789");
            return std::atol(line.c_str() + p);
        }
    return -1;
}

inline void read_faults(uint64_t& minflt, uint64_t& majflt) {
    std::ifstream f("/proc/self/stat");
    std::string s; std::getline(f, s);
    // comm 字段可能含空格，先定位最后一个 ')'，其后按空白切分：
    // token[0]=state(字段3) → minflt(字段10)=token[7]，majflt(字段12)=token[9]
    std::istringstream iss(s.substr(s.rfind(')') + 1));
    std::vector<std::string> tok; for (std::string t; iss >> t;) tok.push_back(t);
    minflt = tok.size() > 7 ? std::stoull(tok[7]) : 0;
    majflt = tok.size() > 9 ? std::stoull(tok[9]) : 0;
}

inline bool read_mem(MemSnap& s) {
    s.vsize = read_status_kb("VmSize") * 1024L;
    s.rss   = read_status_kb("VmRSS")  * 1024L;
    if (s.vsize < 0 || s.rss < 0) return false;
    read_faults(s.minflt, s.majflt);
    return true;
}

inline double rss_mb() { return read_status_kb("VmRSS") / 1024.0; }

// /proc/meminfo 的 MemAvailable（全系统可用量，KB → MB）
inline double system_free_mb() {
    std::ifstream f("/proc/meminfo");
    std::string line;
    while (std::getline(f, line))
        if (line.rfind("MemAvailable", 0) == 0) {
            size_t p = line.find_first_of("0123456789");
            return std::atol(line.c_str() + p) / 1024.0;
        }
    return -1;
}

// 汇总 smaps：Private_Dirty + Private_Clean（本进程独占的物理页总量，KB）
inline long smaps_private_kb() {
    std::ifstream f("/proc/self/smaps");
    std::string line; long total = 0;
    while (std::getline(f, line)) {
        if (line.rfind("Private_Dirty:", 0) == 0 || line.rfind("Private_Clean:", 0) == 0) {
            size_t p = line.find_first_of("0123456789");
            total += std::atol(line.c_str() + p);
        }
    }
    return total;
}

inline bool probe_ok() { return read_status_kb("VmRSS") >= 0; }

#elif defined(__APPLE__)
// ── macOS 通道：libproc 的 proc_taskinfo（一个结构体拿全五项）
inline bool read_taskinfo(struct proc_taskinfo& ti) {
    return proc_pidinfo(getpid(), PROC_PIDTASKINFO, 0, &ti, (int)sizeof(ti)) == (int)sizeof(ti);
}

inline bool read_mem(MemSnap& s) {
    struct proc_taskinfo ti;
    if (!read_taskinfo(ti)) return false;
    s.vsize  = (long)ti.pti_virtual_size;
    s.rss    = (long)ti.pti_resident_size;
    s.minflt = (uint64_t)ti.pti_faults;
    s.majflt = (uint64_t)ti.pti_pageins;
    return true;
}

inline double rss_mb() {
    struct proc_taskinfo ti;
    return read_taskinfo(ti) ? ti.pti_resident_size / 1048576.0 : -1;
}

// macOS 照妖镜：COW 誊抄次数直接可数（vm06 主证据）
inline uint64_t cow_faults() {
    struct proc_taskinfo ti;
    return read_taskinfo(ti) ? (uint64_t)ti.pti_cow_faults : 0;
}

inline double system_free_mb() {
    vm_statistics64_data_t st; mach_msg_type_number_t n = HOST_VM_INFO64_COUNT;
    if (host_statistics64(mach_host_self(), HOST_VM_INFO64, (host_info64_t)&st, &n) != KERN_SUCCESS)
        return -1;
    return st.free_count * (double)getpagesize() / 1048576.0;
}

inline bool probe_ok() { return rss_mb() >= 0 && system_free_mb() >= 0; }
#endif

} // namespace vm_probe

