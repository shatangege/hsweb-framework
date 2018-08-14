package org.hswebframework.web.datasource.strategy;

import org.hswebframework.web.AopUtils;
import org.hswebframework.web.datasource.annotation.UseDataSource;
import org.hswebframework.web.datasource.annotation.UseDefaultDataSource;

import java.lang.reflect.Method;

/**
 * @author zhouhao
 * @since 3.0.0-RC
 */
public class AnnotationDataSourceSwitchStrategyMatcher extends CachedDataSourceSwitchStrategyMatcher {
    @Override
    public Strategy createStrategyIfMatch(Class target, Method method) {
        UseDataSource useDataSource = AopUtils.findAnnotation(target, method, UseDataSource.class);
        UseDefaultDataSource useDefaultDataSource = AopUtils.findAnnotation(target, method, UseDefaultDataSource.class);

        boolean support = useDataSource != null || useDefaultDataSource != null;
        if (support) {
            return new Strategy() {
                @Override
                public boolean isUseDefaultDataSource() {
                    return useDefaultDataSource != null;
                }

                @Override
                public boolean isFallbackDefault() {
                    return useDataSource != null && useDataSource.fallbackDefault();
                }

                @Override
                public String getDataSourceId() {
                    return useDataSource == null ? null : useDataSource.value();
                }

                @Override
                public String toString() {
                    return "Annotation Strategy(" + (useDataSource != null ? useDataSource : useDefaultDataSource) + ")";
                }
            };
        }
        return null;
    }


}
