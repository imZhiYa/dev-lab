package demo10.way;

/**
 * 五通道共用的演示 bean：一个带前缀的打招呼器。
 * 无参构造 + setPrefix 供 XML property 注入使用。
 */
public class Greeter {

    private String prefix;

    public Greeter() {
    }

    public Greeter(String prefix) {
        this.prefix = prefix;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String greet(String name) {
        return prefix + ": " + name;
    }
}
