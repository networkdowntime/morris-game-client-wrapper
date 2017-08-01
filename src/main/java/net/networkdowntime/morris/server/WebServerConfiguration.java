package net.networkdowntime.morris.server;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

@Configuration
@EnableWebMvc
@PropertySource(value = { "classpath:application.properties" })
@ComponentScan(basePackages = "net.networkdowntime.morris")
public class WebServerConfiguration extends WebMvcConfigurerAdapter {

    @Autowired
    private Environment environment;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        //        int cachePeriod = 3600 * 24 * 15;
        //        registry.addResourceHandler("/**").addResourceLocations("/").setCachePeriod(cachePeriod);
        //        registry.addResourceHandler("/favicon.ico").addResourceLocations("/").setCachePeriod(cachePeriod);
        //        registry.addResourceHandler("/robots.txt").addResourceLocations("/").setCachePeriod(cachePeriod);
    }

}