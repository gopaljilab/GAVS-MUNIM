package org.munim.api;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.munim.api.ApiDtos.*;
import org.munim.procurement.ProcurementService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ProcurementController {
    private final ProcurementService service;
    private final IdempotencyService idempotency;
    public ProcurementController(ProcurementService service,IdempotencyService idempotency){this.service=service;this.idempotency=idempotency;}

    @GetMapping("/materials") public Map<String,Object> materials(){return Map.of("items",service.materials());}
    @GetMapping("/suppliers") public Map<String,Object> suppliers(){return Map.of("items",service.suppliers());}
    @PostMapping("/suppliers") @ResponseStatus(HttpStatus.CREATED)
    public SupplierView addSupplier(@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody SupplierCreate input,HttpServletRequest request){
        return idempotency.execute(role(request),"supplier.create",key,input,SupplierView.class,()->service.addSupplier(input));
    }
    @PostMapping("/rfqs") @ResponseStatus(HttpStatus.CREATED)
    public RfqView createRfq(@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody RfqCreate input,HttpServletRequest request){
        return idempotency.execute(role(request),"rfq.create",key,input,RfqView.class,()->service.createRfq(input));
    }
    @GetMapping("/rfqs/{id}") public RfqView getRfq(@PathVariable UUID id){return service.getRfq(id);}
    @PostMapping("/rfqs/{id}/quotes") @ResponseStatus(HttpStatus.CREATED)
    public QuoteView addQuote(@PathVariable UUID id,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody QuoteCreate input,HttpServletRequest request){
        return idempotency.execute(role(request),"quote.create:"+id,key,input,QuoteView.class,()->service.addQuote(id,input));
    }
    @GetMapping("/quotes/{quoteId}/revisions/{revision}")
    public QuoteView getQuote(@PathVariable UUID quoteId,@PathVariable int revision){return service.getQuote(quoteId,revision);}
    @GetMapping("/rfqs/{id}/completeness")
    public Completeness completeness(@PathVariable UUID id,@RequestParam UUID quoteRevisionId){return service.completeness(id,quoteRevisionId);}
    @PostMapping("/rfqs/{id}/evaluations") @ResponseStatus(HttpStatus.CREATED)
    public EvaluationView evaluate(@PathVariable UUID id,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody EvaluationCreate input,HttpServletRequest request){
        if(input.quoteRevisionIds().size()!=1)throw new IllegalArgumentException("First slice evaluates exactly one quote revision");
        return idempotency.execute(role(request),"evaluation.create:"+id,key,input,EvaluationView.class,()->service.evaluate(id,input.quoteRevisionIds().getFirst()));
    }
    @GetMapping("/evaluations/{id}") public EvaluationView getEvaluation(@PathVariable UUID id){return service.getEvaluation(id);}
    private String role(HttpServletRequest r){return r.getAttribute("actorRole").toString();}
}
