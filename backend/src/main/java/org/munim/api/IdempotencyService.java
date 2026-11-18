package org.munim.api;

import tools.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.function.Supplier;

@Service
public class IdempotencyService {
    private final JdbcTemplate db;
    private final ObjectMapper mapper;
    public IdempotencyService(JdbcTemplate db,ObjectMapper mapper){this.db=db;this.mapper=mapper;}

    @Transactional
    public <T>T execute(String actor,String operation,String key,Object request,Class<T> resultType,Supplier<T> action){
        if(key==null||key.length()<8||key.length()>128)throw new IllegalArgumentException("Idempotency-Key must be 8-128 characters");
        String body=write(request),hash=sha(body);
        var records=db.query("select request_sha256,response_body::text,state from idempotency_records where actor_role=? and operation=? and idempotency_key=?",
                (r,n)->new String[]{r.getString(1),r.getString(2),r.getString(3)},actor,operation,key);
        if(!records.isEmpty()){
            String[] row=records.getFirst();
            if(!row[0].equals(hash))throw new IllegalStateException("IDEMPOTENCY_KEY_REUSED");
            if(!"COMPLETED".equals(row[2]))throw new IllegalStateException("IDEMPOTENCY_REQUEST_IN_PROGRESS");
            try{return mapper.readValue(row[1],resultType);}catch(Exception e){throw new IllegalStateException("Stored idempotency response cannot be decoded",e);}
        }
        try {db.update("insert into idempotency_records(actor_role,operation,idempotency_key,request_sha256,state) values (?,?,?,?,'IN_PROGRESS')",actor,operation,key,hash);}
        catch(DuplicateKeyException e){throw new IllegalStateException("IDEMPOTENCY_REQUEST_IN_PROGRESS");}
        T response=action.get();
        db.update("update idempotency_records set response_body=?::jsonb,state='COMPLETED' where actor_role=? and operation=? and idempotency_key=?",write(response),actor,operation,key);
        return response;
    }
    private String write(Object value){try{return mapper.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    private String sha(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
