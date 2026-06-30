package com.zhiya.binary;

/**
 * 高阶空间直角交织位索引：手写 GeoHash 降维打桩机
 * 演示：如何将经纬度浮点切分为二进制流，通过奇偶穿插合并为 64 位整数实现 1D 邻近匹配
 */
public class GeohashBitwiseSpatialIndex {

    private static final int STEP_BITS = 30; // 经纬度各取 30 位精度 (总计 60 位交织流)
    
    // 经典 32 进制呈现码表
    private static final char[] BASE32_MAP = {
        '0','1','2','3','4','5','6','7','8','9','b','c','d','e','f','g',
        'h','j','k','m','n','p','q','r','s','t','u','v','w','x','y','z'
    };

    /**
     * 🔵 将真实的浮点经度与纬度转化并交织合并为一维 64 位二进制整数
     */
    public long encodeInterleave(double lat, double lon) {
        // 1. 分离算出经度与纬度在平面二分切割下的独立二进制码流
        int latBits = encodeDimension(lat, -90.0, 90.0);
        int lonBits = encodeDimension(lon, -180.0, 180.0);

        long interleaveBits = 0L;

        // 2. 🚀 空间神明大招：执行 Z-order 空间填充交织！
        // 奇数插槽埋入纬度 Bit，偶数插槽插设经度 Bit
        for (int i = 0; i < STEP_BITS; i++) {
            // 获取纬度位与经度位当前位线电平
            long latBit = (latBits >>> (STEP_BITS - 1 - i)) & 1L;
            long lonBit = (lonBits >>> (STEP_BITS - 1 - i)) & 1L;

            // 依靠左移位定位插入缝隙，依靠 | 按位或执行极强交织咬合！
            interleaveBits = (interleaveBits << 1) | lonBit;
            interleaveBits = (interleaveBits << 1) | latBit;
        }

        return interleaveBits;
    }

    /**
     * 🟢 二分降解定位：将浮点空间映射至 30 位整数
     */
    private int encodeDimension(double val, double min, double max) {
        int bits = 0;
        for (int i = 0; i < STEP_BITS; i++) {
            double mid = (min + max) / 2.0;
            bits <<= 1;
            if (val >= mid) {
                bits |= 1;
                min = mid;
            } else {
                max = mid;
            }
        }
        return bits;
    }

    /**
     * 🟣 转为肉眼可查问的 Base32 地理标签
     */
    public String toBase32GeoHash(long interleaveBits) {
        StringBuilder sb = new StringBuilder();
        // 60 位数据，每 5 位分块，恰好生成 12 位字符标签
        for (int i = 0; i < 12; i++) {
            int shift = 60 - 5 * (i + 1);
            int index = (int) ((interleaveBits >>> shift) & 0x1F); // 取出 5 位掩码 (31)
            sb.append(BASE32_MAP[index]);
        }
        return sb.toString();
    }

    public static void main(String[] args) {
        GeohashBitwiseSpatialIndex geo = new GeohashBitwiseSpatialIndex();
        System.out.println("==================== [ GeoHash 空间交织寻址演练 ] ====================");

        // 模拟两个地理坐标极其靠近的打车情境 (相距仅百米内)
        double myLat = 30.281234, myLon = 120.021345;  // 当前乘客落点
        double carLat = 30.281299, carLon = 120.021312;// 周围接单快车落点

        long myGeoBits = geo.encodeInterleave(myLat, myLon);
        long carGeoBits = geo.encodeInterleave(carLat, carLon);

        String myGeoHash = geo.toBase32GeoHash(myGeoBits);
        String carGeoHash = geo.toBase32GeoHash(carGeoBits);

        System.out.println("📍 乘客物理实态 (30.281234, 120.021345)");
        System.out.println("   交织位线: " + BinaryUtils.toPrettyBinary((int)(myGeoBits >>> 32))); // 查看上层 32 位码
        System.out.println("   GeoHash 码: " + myGeoHash);
        
        System.out.println("🚗 车辆物理实态 (30.281299, 120.021312)");
        System.out.println("   GeoHash 码: " + carGeoHash);

        System.out.println("🔍 邻近前缀直接匹配断言: [" + myGeoHash.substring(0, 7) + "] (前置字符 100% 严密自洽，将二维遍历压成一维前缀秒搜！)");
    }
}
