package com.zhiya.ddd.demo;

/**
 * EX-07 DDD 选型矩阵 —— 验证 ddd-06 的"选择 DDD 的理由"：
 *  给模块打分（维度与权重来自 ddd-06 决策卡，示例值），输出推荐方向。
 *
 * 维度（1-5）：
 *  - diff     子域差异度：语义多、子域多 -> 高
 *  - density  规则密度：状态机/不变量多 -> 高
 *  - change   变更频率：规则常改 -> 高（DDD 的模型化收益才兑现）
 *  - cost     错误成本：金额/合规/资损 -> 高
 *  - collab   协作复杂性：多方协作、语言不统一 -> 高
 *  - consist  一致性要求：强一致性边界 -> 高
 *
 * 推荐规则（示例阈值，权重=每项 1.0，总分 6-30）：
 *  - 总分 >= 20   : DDD 核心域：模型驱动 + 通用语言 + 事件溯源（可上 DDD）
 *  - 总分 13-19   : 模块化 + 规则库：先做边界与防腐，不全量上 DDD
 *  - 总分 < 13    : CRUD 域：薄服务 + 事务脚本（禁止为了"规范"上 DDD）
 */
public final class Ex07DddDecisionMatrixDemo {

    public record ModuleScore(String module, int diff, int density, int change, int cost, int collab, int consist) {
        public ModuleScore {
            for (int v : new int[]{diff, density, change, cost, collab, consist}) {
                if (v < 1 || v > 5) {
                    throw new IllegalArgumentException("score must be 1-5, got " + v);
                }
            }
        }

        public int total() {
            return diff + density + change + cost + collab + consist;
        }
    }

    public static String recommend(ModuleScore s) {
        if (s.total() >= 20) {
            return "DDD 核心域";
        } else if (s.total() >= 13) {
            return "模块化+规则库";
        } else {
            return "CRUD 薄服务";
        }
    }

    public static void main(String[] args) {
        Checks c = new Checks();

        // 核心域：策略配置（状态机+规则+高变更+影响流量分配）-> DDD 核心域
        ModuleScore strategy = new ModuleScore("strategy", 5, 5, 4, 4, 4, 4);
        c.checkEq("策略配置 -> DDD 核心域", "DDD 核心域", recommend(strategy));

        // 支撑域：内容审核（规则集中、外部集成多，但变更低）-> 模块化+规则库
        ModuleScore moderation = new ModuleScore("moderation", 3, 3, 2, 4, 4, 3);
        c.checkEq("内容审核 -> 模块化+规则库", "模块化+规则库", recommend(moderation));

        // 通用域：字典表（纯 CRUD）-> 薄服务，禁止上 DDD
        ModuleScore dict = new ModuleScore("dict", 1, 1, 1, 1, 1, 2);
        c.checkEq("字典表 -> CRUD 薄服务", "CRUD 薄服务", recommend(dict));

        // 边界：维度非法 -> 构造即拒绝（选型工具本身也要守值对象纪律）
        boolean rejected = false;
        try {
            new ModuleScore("bad", 0, 1, 1, 1, 1, 1);
        } catch (IllegalArgumentException e) {
            rejected = true;
        }
        c.check("维度越界被拒绝", rejected);

        c.summary("Ex07");
    }
}