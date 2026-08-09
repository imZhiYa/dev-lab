package demo17.app;

import demo17.permcheck.RequireRole;
import org.springframework.stereotype.Service;

/**
 * 权限切面 starter 的业务使用方（demo17 实证）：
 * 业务方法只写"需要什么角色"，校验语义由 starter 的切面承担（06 篇决策卡 3：
 * 注解把"谁被拦"显式化）。
 */
@Service
public class OrderService {

    @RequireRole("ADMIN")
    public void adminOnly() {
        System.out.println("[放行] 管理员操作执行成功");
    }
}
