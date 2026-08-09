package demo17.permcheck;

/**
 * 权限校验失败异常（demo17 实证）：无权限调用被切面拦截时抛出。
 * 真实系统通常抛 AccessDeniedException（Spring Security）或转 403；
 * 此处自定义以零依赖演示"切面决定拒绝语义"。
 */
public class PermissionDeniedException extends RuntimeException {

    public PermissionDeniedException(String message) {
        super(message);
    }
}
