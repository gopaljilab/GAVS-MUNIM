package org.munim.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;

@Component
public class DemoAuthFilter extends OncePerRequestFilter {
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
        String path=req.getRequestURI();
        if (!path.startsWith("/api/v1/")) { chain.doFilter(req,res); return; }
        if (req.getMethod().equals("OPTIONS")) { chain.doFilter(req,res); return; }
        String auth=req.getHeader("Authorization");
        String role=auth==null?null:switch(auth) {
            case "Bearer dev-owner" -> "OWNER";
            case "Bearer dev-procurement" -> "PROCUREMENT_OPERATOR";
            case "Bearer dev-evaluator" -> "READ_ONLY_EVALUATOR";
            default -> null;
        };
        if(role==null){writeProblem(res,401,"UNAUTHORIZED","Bearer token required");return;}
        Set<String> allowed=allowed(req.getMethod(),path);
        if(!allowed.isEmpty()&&!allowed.contains(role)){writeProblem(res,403,"FORBIDDEN","Insufficient role");return;}
        if(path.startsWith("/api/v1/demo")){
            String demoEnabled=System.getenv("MUNIM_DEMO_ENABLED");
            if("false".equalsIgnoreCase(demoEnabled)){writeProblem(res,403,"DEMO_DISABLED","Demo seeding is disabled in this environment");return;}
        }
        req.setAttribute("actorRole",role);
        req.setAttribute("actorId",auth.substring("Bearer ".length()));
        chain.doFilter(req,res);
    }
    private void writeProblem(HttpServletResponse res,int status,String code,String detail)throws IOException{
        res.setStatus(status);res.setContentType("application/problem+json");
        res.getWriter().write("{\"type\":\"about:blank\",\"title\":\""+(status==401?"Unauthorized":"Forbidden")+"\",\"status\":"+status+",\"detail\":\""+detail+"\",\"code\":\""+code+"\",\"fieldErrors\":{},\"traceId\":\""+UUID.randomUUID()+"\"}");
    }
    private Set<String> allowed(String method,String path){
        if(path.equals("/api/v1/owner-policy")||path.startsWith("/api/v1/approvals")||path.contains("/decision")||path.startsWith("/api/v1/demo"))return Set.of("OWNER");
        if(method.equals("GET")&&(path.equals("/api/v1/materials")||path.equals("/api/v1/suppliers")))return Set.of("OWNER","PROCUREMENT_OPERATOR");
        if(method.equals("POST")&&path.equals("/api/v1/rfqs"))return Set.of("OWNER","PROCUREMENT_OPERATOR");
        if(method.equals("POST")&&path.equals("/api/v1/suppliers"))return Set.of("OWNER","PROCUREMENT_OPERATOR");
        if(method.equals("POST")&&path.matches("/api/v1/rfqs/[^/]+/quotes"))return Set.of("OWNER","PROCUREMENT_OPERATOR");
        if(method.equals("GET")&&path.startsWith("/api/v1/"))return Set.of("OWNER","PROCUREMENT_OPERATOR","READ_ONLY_EVALUATOR");
        return Set.of("OWNER","PROCUREMENT_OPERATOR");
    }
}
