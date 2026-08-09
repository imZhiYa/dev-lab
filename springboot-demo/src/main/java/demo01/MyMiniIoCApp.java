package demo01;

public class MyMiniIoCApp {

    public static void main(String[] args) throws Exception {
        MyMiniIoC container = new MyMiniIoC();
        container.register("orderService", OrderService.class);
        container.register("userService", CtorUserService.class);

        CtorUserService a = (CtorUserService) container.getBean("userService");
        CtorUserService b = (CtorUserService) container.getBean("userService");

        System.out.println("两次 getBean 是否同一实例（单例池生效）: " + (a == b));
        System.out.println("构造器依赖注入成功: " + (a.getOrderService() != null));
    }
}

class OrderService {
    public String create() {
        return "order created";
    }
}

/**
 * 构造器注入版（供 MyMiniIoC 演示"按构造器参数类型查注册表"）
 */
class CtorUserService {
    private final OrderService orderService;

    public CtorUserService(OrderService orderService) {
        this.orderService = orderService;
    }

    public OrderService getOrderService() {
        return orderService;
    }
}
