package com.zhiya.dubbo.demo.spi;

import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.rpc.Filter;
import com.zhiya.dubbo.demo.spi.extension.DemoProviderFilter;

import java.util.List;

/**
 * E09-3：@Activate——按 group 自动激活的 Filter（此处看 provider 侧）。
 * 演示：激活类探测、group 匹配、激活链列表（含 SPI 文件与 dubbo jar 内置合并）。
 */
public class ActivateApp {

    public static void main(String[] args) {
        ExtensionLoader<Filter> loader = ExtensionLoader.getExtensionLoader(Filter.class);
        System.out.println("=== [E09-3] filter names: " + loader.getSupportedExtensions());

        Activate ann = DemoProviderFilter.class.getAnnotation(Activate.class);
        System.out.println("=== [E09-3] DemoProviderFilter @Activate(group=" + String.join(",", ann.group())
                + ", value=" + String.join(",", ann.value()) + ", order=" + ann.order() + ")");

        List<Filter> providerFilters = loader.getActivateExtension(
                org.apache.dubbo.common.URL.valueOf("tri://127.0.0.1:50051"), new String[0], "provider");
        System.out.println("=== [E09-3] provider-side activated filters = " + providerFilters.size());
        for (Filter f : providerFilters) {
            System.out.println("=== [E09-3]   -> " + f.getClass().getName());
        }
        System.out.println("=== [E09-3] contains DemoProviderFilter? " + providerFilters.stream()
                .anyMatch(f -> f instanceof DemoProviderFilter));
    }
}
