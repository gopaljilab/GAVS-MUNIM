package org.munim.procurement;

import tools.jackson.databind.ObjectMapper;
import org.munim.api.ApiDtos.*;
import org.munim.financial.FinancialEvaluator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class ProcurementService {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final FinancialEvaluator evaluator;
    public ProcurementService(JdbcTemplate db,ObjectMapper json,FinancialEvaluator evaluator){this.db=db;this.json=json;this.evaluator=evaluator;}

    public List<Map<String,Object>> materials(){return db.queryForList("select id,name,grade,specification,base_unit as \"baseUnit\",active from materials where active order by name");}
    public List<SupplierView> suppliers(){return db.query("select id,name,location,is_synthetic,active from suppliers where active order by name",(r,n)->new SupplierView(r.getObject("id",UUID.class),r.getString("name"),r.getString("location"),r.getBoolean("is_synthetic"),r.getBoolean("active")));}
    @Transactional public SupplierView addSupplier(SupplierCreate in){
        if(!Boolean.TRUE.equals(in.isSynthetic()))throw new IllegalArgumentException("Demo suppliers must be labeled synthetic");
        UUID id=db.queryForObject("insert into suppliers(name,location,is_synthetic) values (?,?,true) returning id",UUID.class,in.name().trim(),in.location());
        return new SupplierView(id,in.name().trim(),in.location(),true,true);
    }
    @Transactional public RfqView createRfq(RfqCreate in){
        if(in.allowSplitSourcing())throw new IllegalArgumentException("Split sourcing is disabled in the MVP");
        if(!"kg".equalsIgnoreCase(in.unit()))throw new IllegalArgumentException("Only material base unit kg is supported in the first slice");
        if(in.quantity().scale()>4)throw new IllegalArgumentException("quantity supports at most four decimal places");
        if(in.productionLeadDays()!=null&&in.productionLeadDays()<0)throw new IllegalArgumentException("productionLeadDays must be non-negative");
        OffsetDateTime delivery=in.requiredProcurementDeliveryAt();
        if(in.customerRequiredBy()!=null&&in.productionLeadDays()!=null){
            OffsetDateTime derived=in.customerRequiredBy().minusDays(in.productionLeadDays());
            if(delivery!=null&&!delivery.equals(derived))throw new IllegalArgumentException("Conflicting procurement deadline inputs");
            delivery=derived;
        }
        if(delivery==null)throw new IllegalArgumentException("Provide procurement deadline or customer deadline plus production lead days");
        OffsetDateTime now=OffsetDateTime.now();
        UUID id=db.queryForObject("insert into rfqs(material_id,specification,quantity,unit,production_lead_days,customer_required_by,required_procurement_delivery_at,reference_at,timezone,allow_split_sourcing,status) values (?,?,?,?,?,?,?,?,?,false,'OPEN') returning id",
                UUID.class,in.materialId(),in.specification(),in.quantity(),in.unit(),in.productionLeadDays(),in.customerRequiredBy(),delivery,now,in.timezone());
        return getRfq(id);
    }
    public RfqView getRfq(UUID id){
        return db.queryForObject("select id,material_id,specification,quantity,unit,production_lead_days,customer_required_by,required_procurement_delivery_at,reference_at,timezone,status from rfqs where id=?",
                (r,n)->new RfqView(r.getObject("id",UUID.class),r.getObject("material_id",UUID.class),r.getString("specification"),r.getBigDecimal("quantity").toPlainString(),r.getString("unit"),(Integer)r.getObject("production_lead_days"),r.getObject("customer_required_by",OffsetDateTime.class),r.getObject("required_procurement_delivery_at",OffsetDateTime.class),r.getObject("reference_at",OffsetDateTime.class),r.getString("timezone"),r.getString("status")),id);
    }
    @Transactional public QuoteView addQuote(UUID rfqId,QuoteCreate in){
        getRfq(rfqId);
        if(!Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from suppliers where id=? and active)",Boolean.class,in.supplierId())))
            throw new NoSuchElementException("Supplier not found");
        UUID quoteId=db.queryForObject("insert into supplier_quotes(rfq_id,supplier_id) values (?,?) returning id",UUID.class,rfqId,in.supplierId());
        String rawHash=sha(in.rawMessage());
        Map<String,Object> fields=in.manualFields();
        Set<String> missing=evaluator.missing(fields,"kg",new java.math.BigDecimal(getRfq(rfqId).quantity()));
        String completeness=missing.isEmpty()?"COMPLETE":"INCOMPLETE";
        UUID revisionId=db.queryForObject("insert into supplier_quote_revisions(supplier_quote_id,rfq_id,revision_no,state,received_at,raw_message,raw_message_sha256,fields,completeness_status) values (?,?,1,?,?,?, ?,?::jsonb,?) returning id",
                UUID.class,quoteId,rfqId,"RECEIVED",in.receivedAt(),in.rawMessage(),rawHash,write(fields),completeness);
        db.update("update supplier_quotes set current_revision_id=? where id=?",revisionId,quoteId);
        return new QuoteView(quoteId,in.supplierId(),revisionId,1,"RECEIVED",in.rawMessage(),completeness,List.copyOf(missing),fields);
    }
    public QuoteView getQuote(UUID quoteId,int revisionNo){
        return db.queryForObject("select sq.id,sq.supplier_id,r.id revision_id,r.revision_no,r.state,r.raw_message,r.completeness_status,r.fields,q.quantity,q.unit from supplier_quotes sq join supplier_quote_revisions r on r.supplier_quote_id=sq.id join rfqs q on q.id=sq.rfq_id where sq.id=? and r.revision_no=?",
                (r,n)->{
                    Map<String,Object> fields=read(r.getString("fields"));
                    Set<String> missing=evaluator.missing(fields,r.getString("unit"),r.getBigDecimal("quantity"));
                    return new QuoteView(r.getObject("id",UUID.class),r.getObject("supplier_id",UUID.class),r.getObject("revision_id",UUID.class),r.getInt("revision_no"),r.getString("state"),r.getString("raw_message"),r.getString("completeness_status"),List.copyOf(missing),fields);
                },quoteId,revisionNo);
    }
    public Completeness completeness(UUID rfqId,UUID revisionId){
        var row=db.queryForObject("select r.fields,q.quantity,q.unit from supplier_quote_revisions r join supplier_quotes sq on sq.id=r.supplier_quote_id join rfqs q on q.id=sq.rfq_id where q.id=? and r.id=?",
                (rs,n)->new Object[]{read(rs.getString("fields")),rs.getBigDecimal("quantity"),rs.getString("unit")},rfqId,revisionId);
        Map<String,Object> f=(Map<String,Object>)row[0];
        Set<String> missing=evaluator.missing(f,(String)row[2],(java.math.BigDecimal)row[1]);
        return new Completeness(revisionId,missing.isEmpty(),List.copyOf(missing),List.of());
    }
    @Transactional public EvaluationView evaluate(UUID rfqId,UUID revisionId){
        var row=db.queryForObject("select r.fields,r.raw_message_sha256,q.quantity,q.unit,q.required_procurement_delivery_at from supplier_quote_revisions r join supplier_quotes sq on sq.id=r.supplier_quote_id join rfqs q on q.id=sq.rfq_id where q.id=? and r.id=?",
                (rs,n)->new Object[]{read(rs.getString("fields")),rs.getString("raw_message_sha256"),rs.getBigDecimal("quantity"),rs.getString("unit"),rs.getObject("required_procurement_delivery_at",OffsetDateTime.class)},rfqId,revisionId);
        Map<String,Object> fields=(Map<String,Object>)row[0];
        OffsetDateTime evaluationAt=OffsetDateTime.now();
        OffsetDateTime promised=OffsetDateTime.parse(fields.get("promisedDeliveryAt").toString());
        OffsetDateTime validFrom=OffsetDateTime.parse(fields.get("validFrom").toString());
        OffsetDateTime validUntil=OffsetDateTime.parse(fields.get("validUntil").toString());
        OffsetDateTime required=(OffsetDateTime)row[4];
        if(promised.isAfter(required))throw new IllegalStateException("Supplier delivery is after required procurement deadline");
        if(evaluationAt.isBefore(validFrom)||evaluationAt.isAfter(validUntil))throw new IllegalStateException("Supplier quote is not valid at evaluation time");
        Set<String> missing=evaluator.missing(fields,(String)row[3],(java.math.BigDecimal)row[2]);
        if(!missing.isEmpty())throw new FinancialEvaluator.IncompleteQuoteException(missing);
        var result=evaluator.evaluate(fields,(java.math.BigDecimal)row[2],(String)row[3]);
        Map<String,Object> snapshot=new LinkedHashMap<>(fields);snapshot.put("rawMessageSha256",row[1]);snapshot.put("rfqQuantity",((java.math.BigDecimal)row[2]).toPlainString());snapshot.put("rfqUnit",row[3]);snapshot.put("requiredDeliveryAt",row[4].toString());
        String snapshotJson=write(snapshot),hash=sha(snapshotJson),resultJson=write(result);
        OffsetDateTime reference=evaluationAt;
        UUID evalId=db.queryForObject("insert into quote_evaluations(rfq_id,quote_revision_id,reference_at,calculation_version,input_snapshot,snapshot_sha256,status,result) values (?,?,?,'1.1',?::jsonb,?,'COMPLETE',?::jsonb) returning id",
                UUID.class,rfqId,revisionId,reference,snapshotJson,hash,resultJson);
        db.update("insert into evaluation_lines(evaluation_id,quote_revision_id,material_base,material_gst,freight_base,freight_gst,other_charges,economic_landed_cost,cash_payable) values (?,?,?,?,?,?,0,?,?)",
                evalId,revisionId,
                new java.math.BigDecimal(result.get("materialBase").toString()),
                new java.math.BigDecimal(result.get("materialGst").toString()),
                new java.math.BigDecimal(result.get("freightBase").toString()),
                new java.math.BigDecimal(result.get("freightGst").toString()),
                new java.math.BigDecimal(result.get("economicLandedCost").toString()),
                new java.math.BigDecimal(result.get("cashPayable").toString()));
        return new EvaluationView(evalId,rfqId,revisionId,"COMPLETE","1.1",hash,snapshot,result);
    }
    public EvaluationView getEvaluation(UUID id){
        return db.queryForObject("select id,rfq_id,quote_revision_id,status,calculation_version,snapshot_sha256,input_snapshot,result from quote_evaluations where id=?",
                (r,n)->new EvaluationView(r.getObject("id",UUID.class),r.getObject("rfq_id",UUID.class),r.getObject("quote_revision_id",UUID.class),r.getString("status"),r.getString("calculation_version"),r.getString("snapshot_sha256"),read(r.getString("input_snapshot")),read(r.getString("result"))),id);
    }
    private String write(Object o){try{return json.writeValueAsString(o);}catch(Exception e){throw new IllegalStateException(e);}}
    @SuppressWarnings("unchecked") private Map<String,Object> read(String s){try{return (Map<String,Object>)json.readValue(s,Map.class);}catch(Exception e){throw new IllegalStateException(e);}}
    private String sha(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
