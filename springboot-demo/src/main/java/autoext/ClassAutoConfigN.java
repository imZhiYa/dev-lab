package autoext;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/**
 * 类条件 Negative 实证：com.example.never.Exists 不在任何 classpath（本实验无此依赖）。
 * 启动全程不抛 NoClassDefFoundError —— @ConditionalOnClass 通过 ASM 读 class 元数据判断，
 * 而不是 Class.forName 加载（demo10.ClassConditionApp 验证）。
 */
@AutoConfiguration
@ConditionalOnClass(name = "com.example.never.Exists")
public class ClassAutoConfigN {

    @Bean("demo10-class-n")
    public String classN() {
        return "class-n";
    }
}
