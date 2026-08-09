package autoext;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/**
 * 类条件 Positive 实证：@ConditionalOnClass(name=...) 用 ASM 元数据检测类是否在 classpath，
 * 不触发类加载（demo10.ClassConditionApp 验证不存在的类不抛 NoClassDefFoundError）。
 */
@AutoConfiguration
@ConditionalOnClass(name = "java.util.ArrayList")
public class ClassAutoConfigP {

    @Bean("demo10-class-p")
    public String classP() {
        return "class-p";
    }
}
