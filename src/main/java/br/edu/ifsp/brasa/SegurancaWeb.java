package br.edu.ifsp.brasa;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SegurancaWeb implements WebMvcConfigurer {
    public static boolean iguais(String esperado, String recebido) {
        return esperado != null && recebido != null && MessageDigest.isEqual(
                esperado.getBytes(StandardCharsets.UTF_8), recebido.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void addInterceptors(InterceptorRegistry registro) {
        registro.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                response.setHeader("Cache-Control", "no-store");
                HttpSession sessao = request.getSession(!request.getMethod().equals("POST"));
                String csrf = sessao == null ? null : (String) sessao.getAttribute("csrf");
                if (request.getMethod().equals("POST")) {
                    if (!iguais(csrf, request.getParameter("csrf"))) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
                    }
                } else if (csrf == null) {
                    sessao.setAttribute("csrf", UUID.randomUUID().toString());
                }
                return true;
            }
        }).excludePathPatterns("/estilo.css", "/carrinho.js", "/fotos/**", "/favicon.ico", "/error");
    }
}
