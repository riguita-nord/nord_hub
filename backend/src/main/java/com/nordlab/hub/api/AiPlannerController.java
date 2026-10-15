package com.nordlab.hub.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nordlab.hub.domain.WorkspaceData;
import com.nordlab.hub.repository.WorkspaceDataRepository;
import com.nordlab.hub.service.CurrentUserService;
import com.nordlab.hub.service.WorkspaceAccessService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/planner")
public class AiPlannerController {
    private final WorkspaceDataRepository data;private final WorkspaceAccessService access;private final CurrentUserService current;private final ObjectMapper mapper;private final RestClient client;private final String model;private final boolean enabled;
    public AiPlannerController(WorkspaceDataRepository data,WorkspaceAccessService access,CurrentUserService current,ObjectMapper mapper,RestClient.Builder builder,
            @Value("${nordhub.ai.api-key:}")String key,@Value("${nordhub.ai.base-url:https://api.openai.com/v1}")String baseUrl,@Value("${nordhub.ai.model:gpt-4o-mini}")String model){
        this.data=data;this.access=access;this.current=current;this.mapper=mapper;this.model=model;this.enabled=!key.isBlank();this.client=builder.baseUrl(baseUrl).defaultHeader("Authorization","Bearer "+key).defaultHeader("Content-Type","application/json").build();
    }
    @PostMapping("/suggestions")
    public SuggestionResponse suggest(@PathVariable UUID workspaceId,@Valid @RequestBody PlanRequest request){
        access.require(workspaceId,current.id(),false);if(!enabled)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Planeamento IA opcional não está configurado no servidor");
        List<Map<String,Object>> tasks=data.findAllByWorkspaceIdAndRecordTypeOrderByUpdatedAtDesc(workspaceId,"TASK").stream().filter(t->!Boolean.TRUE.equals(t.getPayload().get("done"))).map(this::plannerTask).toList();
        if(tasks.isEmpty())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Não existem tarefas abertas para planear");
        String prompt="Cria uma proposta semanal de programação para as tarefas fornecidas. Devolve JSON com summary (string) e suggestions (array de {taskId,day,start,durationMinutes,reason}). Usa só os taskId fornecidos, estima durações realistas, respeita disponibilidade de "+request.availableHours()+" horas e dá prioridade a prazos urgentes. Não marques tarefas como concluídas nem alteres nada. Tarefas: "+write(tasks);
        try{JsonNode result=client.post().uri("/chat/completions").body(Map.of("model",model,"temperature",0.2,"messages",List.of(Map.of("role","system","content","És um assistente de planeamento. Retorna apenas JSON válido. As sugestões carecem sempre de aprovação humana."),Map.of("role","user","content",prompt)))).retrieve().body(JsonNode.class);
            String content=result==null?"":result.path("choices").path(0).path("message").path("content").asText("");if(content.isBlank())throw new IllegalStateException("AI provider returned an empty response");
            String json=content.replaceAll("(?s)^\\s*```(?:json)?\\s*|\\s*```\\s*$","");JsonNode parsed=mapper.readTree(json);return new SuggestionResponse(parsed.path("summary").asText("Proposta pronta para rever"),parsed.path("suggestions"),model);
        }catch(ResponseStatusException e){throw e;}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"O serviço de planeamento não respondeu num formato válido");}
    }
    private Map<String,Object> plannerTask(WorkspaceData row){Map<String,Object> p=row.getPayload();return Map.of("taskId",row.getId().toString(),"title",String.valueOf(p.getOrDefault("title","")),"projectId",String.valueOf(p.getOrDefault("projectId","")),"priority",String.valueOf(p.getOrDefault("priority","Média")),"due",String.valueOf(p.getOrDefault("due","")),"estimateMinutes",p.getOrDefault("estimate",30));}
    private String write(Object value){try{return mapper.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    public record PlanRequest(@Min(1) @Max(80) int availableHours){}
    public record SuggestionResponse(String summary,JsonNode suggestions,String model){}
}
