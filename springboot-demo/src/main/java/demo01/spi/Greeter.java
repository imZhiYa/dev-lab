package demo01.spi;

/** SPI 接口：JDK ServiceLoader 只负责"发现实现"，发现之后无人管理。 */
public interface Greeter {
    String hello();
}
