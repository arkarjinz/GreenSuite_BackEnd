package com.app.greensuitetest.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.jedis.cas.JedisBasedProxyManager;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Configuration
public class RateLimitingConfig {

    @Bean
    public JedisPool jedisPool(RedisProperties redisProperties) {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(redisProperties.getJedis().getPool().getMaxActive());
        poolConfig.setMaxIdle(redisProperties.getJedis().getPool().getMaxIdle());
        poolConfig.setMinIdle(redisProperties.getJedis().getPool().getMinIdle());

        // Disable JMX to prevent MBean registration conflicts
        poolConfig.setJmxEnabled(false);

        return new JedisPool(
                poolConfig,
                redisProperties.getHost(),
                redisProperties.getPort(),
                (int) redisProperties.getTimeout().toMillis(),
                redisProperties.getPassword(),
                redisProperties.getDatabase()
        );
    }

    @Bean
    public ProxyManager<byte[]> proxyManager(JedisPool jedisPool) {
        return JedisBasedProxyManager.builderFor(jedisPool)
                .build();
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilter(ProxyManager<byte[]> proxyManager) {
        FilterRegistrationBean<RateLimitFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new RateLimitFilter(proxyManager));
        registrationBean.addUrlPatterns("/api/ai/chat");
        registrationBean.setOrder(1);
        return registrationBean;
    }

    public static class RateLimitFilter implements Filter {
        private final ProxyManager<byte[]> proxyManager;

        public RateLimitFilter(ProxyManager<byte[]> proxyManager) {
            this.proxyManager = proxyManager;
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {

            HttpServletRequest httpRequest = (HttpServletRequest) request;
            HttpServletResponse httpResponse = (HttpServletResponse) response;

            String clientIp = getClientIp(httpRequest);
            byte[] key = ("rl_" + clientIp).getBytes(StandardCharsets.UTF_8);

            Bandwidth bandwidth = Bandwidth.builder()
                    .capacity(10)
                    .refillIntervally(10, Duration.ofMinutes(1))
                    .build();

            BucketConfiguration configuration = BucketConfiguration.builder()
                    .addLimit(bandwidth)
                    .build();

            Bucket bucket = proxyManager.builder().build(key, configuration);

            if (bucket.tryConsume(1)) {
                chain.doFilter(request, response);
            } else {
                httpResponse.setStatus(429);
                httpResponse.setContentType("application/json");
                httpResponse.getWriter().write("{\"error\":\"Rate limit exceeded. Please try again later.\"}");
            }
        }

        private String getClientIp(HttpServletRequest request) {
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                return xForwardedFor.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        }
    }
}