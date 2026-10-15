package com.nordlab.hub.service;

import com.nordlab.hub.domain.WorkspaceData;
import com.nordlab.hub.repository.WorkspaceDataRepository;
import com.nordlab.hub.repository.WorkspaceMemberRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WorkspaceDataService {
    private static final Map<String,String> COLLECTIONS=Map.of("projects","PROJECT","tasks","TASK","ideas","IDEA","notes","NOTE","events","CALENDAR_EVENT","releases","RELEASE","notifications","NOTIFICATION");
    private static final Map<String,Set<String>> FIELDS=Map.of(
        "PROJECT",Set.of("name","description","goal","status","priority","progress","due","icon","isFocus"),
        "TASK",Set.of("title","description","projectId","assigneeId","status","priority","due","estimate","done","notes","checklist","dependsOn"),
        "IDEA",Set.of("title","description","created"),
        "NOTE",Set.of("title","body","projectId","updated","references"),
        "CALENDAR_EVENT",Set.of("title","day","startAt","endAt","duration","kind","color","projectId"),
        "RELEASE",Set.of("title","version","projectId","status","date","changelog","testPlan","tasks","taskIds"),
        "NOTIFICATION",Set.of("title","body","kind","time","readAt","dueAt"));
    private final WorkspaceDataRepository data;private final WorkspaceMemberRepository members;
    public WorkspaceDataService(WorkspaceDataRepository data,WorkspaceMemberRepository members){this.data=data;this.members=members;}
    public String resolveType(String collection){String type=COLLECTIONS.get(collection);if(type==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Unknown workspace module");return type;}
    @Transactional(readOnly=true) public List<DataResponse> list(UUID workspaceId,String type){return data.findAllByWorkspaceIdAndRecordTypeOrderByUpdatedAtDesc(workspaceId,type).stream().map(this::response).toList();}
    @Transactional public DataResponse create(UUID workspaceId,UUID actor,String type,Map<String,Object> input){Map<String,Object> payload=validate(workspaceId,type,input);WorkspaceData record=data.save(new WorkspaceData(UUID.randomUUID(),workspaceId,type,payload,actor));return response(record);}
    @Transactional public DataResponse patch(UUID workspaceId,UUID id,UUID actor,String type,Map<String,Object> changes){WorkspaceData record=find(workspaceId,id,type);Map<String,Object> merged=new LinkedHashMap<>(record.getPayload());merged.putAll(changes);Map<String,Object> payload=validate(workspaceId,type,merged);record.replacePayload(payload);return response(record);}
    @Transactional public void delete(UUID workspaceId,UUID id,String type){data.delete(find(workspaceId,id,type));}
    private WorkspaceData find(UUID workspaceId,UUID id,String type){return data.findByIdAndWorkspaceIdAndRecordType(id,workspaceId,type).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Record not found"));}
    private Map<String,Object> validate(UUID workspaceId,String type,Map<String,Object> input){
        if(input==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"A JSON object is required");
        Map<String,Object> clean=new LinkedHashMap<>();Set<String> allowed=FIELDS.get(type);
        input.forEach((key,value)->{if(allowed.contains(key)&&value!=null)clean.put(key,value);});
        String required=(type.equals("PROJECT"))?"name":(type.equals("CALENDAR_EVENT")?"title":(type.equals("RELEASE")?"version":"title"));
        String text=clean.get(required) instanceof String s?s.trim():"";
        if(text.isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,required+" is required");
        int max=required.equals("description")?500:160;if(text.length()>max)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,required+" is too long");clean.put(required,text);
        if(clean.get("description") instanceof String d&&d.length()>2000)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"description is too long");
        if(clean.containsKey("status")){String status=String.valueOf(clean.get("status"));if(!Set.of("Em Foco","Em Desenvolvimento","Em Espera","Bloqueado","Concluído","Arquivado","Por Fazer","Em Curso","Em Testes","Bloqueada","Em preparação","Planeada").contains(status))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid status");}
        if(clean.containsKey("priority")&&!Set.of("Alta","Média","Baixa").contains(String.valueOf(clean.get("priority"))))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid priority");
        if(clean.containsKey("progress")){int progress=integer(clean.get("progress"),"progress");if(progress<0||progress>100)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"progress must be between 0 and 100");clean.put("progress",progress);}
        if(clean.containsKey("estimate")){int estimate=integer(clean.get("estimate"),"estimate");if(estimate<0||estimate>10080)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"estimate must be between 0 and 10080 minutes");clean.put("estimate",estimate);}
        if(clean.containsKey("day")){int day=integer(clean.get("day"),"day");if(day<1||day>31)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"day must be between 1 and 31");clean.put("day",day);}
        if(clean.containsKey("projectId")){try{UUID projectId=UUID.fromString(String.valueOf(clean.get("projectId")));if(!data.existsByIdAndWorkspaceIdAndRecordType(projectId,workspaceId,"PROJECT"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"projectId must belong to this workspace");clean.put("projectId",projectId.toString());}catch(IllegalArgumentException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"projectId must be a valid UUID");}}
        if(clean.containsKey("assigneeId")&&!String.valueOf(clean.get("assigneeId")).isBlank()){try{UUID assignee=UUID.fromString(String.valueOf(clean.get("assigneeId")));if(!members.existsByWorkspaceIdAndUserId(workspaceId,assignee))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"assigneeId must be a member of this workspace");clean.put("assigneeId",assignee.toString());}catch(IllegalArgumentException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"assigneeId must be a valid UUID");}}
        if(clean.containsKey("checklist")){if(!(clean.get("checklist") instanceof List<?> items)||items.size()>30)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"checklist must contain at most 30 items");}
        if(clean.containsKey("dependsOn")){if(!(clean.get("dependsOn") instanceof List<?> dependencies)||dependencies.size()>20)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"dependsOn must contain at most 20 task ids");for(Object value:dependencies){try{UUID taskId=UUID.fromString(String.valueOf(value));if(!data.existsByIdAndWorkspaceIdAndRecordType(taskId,workspaceId,"TASK"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"dependencies must reference tasks in this workspace");}catch(IllegalArgumentException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"dependency id must be a valid UUID");}}}
        if(clean.containsKey("taskIds")){if(!(clean.get("taskIds") instanceof List<?> taskIds)||taskIds.size()>100)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"taskIds must contain at most 100 items");for(Object value:taskIds){try{UUID taskId=UUID.fromString(String.valueOf(value));if(!data.existsByIdAndWorkspaceIdAndRecordType(taskId,workspaceId,"TASK"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"release tasks must belong to this workspace");}catch(IllegalArgumentException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"release task id must be a valid UUID");}}clean.put("tasks",taskIds.size());}
        return clean;
    }
    private int integer(Object value,String field){try{return value instanceof Number n?n.intValue():Integer.parseInt(String.valueOf(value));}catch(NumberFormatException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,field+" must be an integer");}}
    private DataResponse response(WorkspaceData data){return new DataResponse(data.getId(),data.getRecordType(),data.getCreatedAt(),data.getUpdatedAt(),data.getPayload());}
    public record DataResponse(UUID id,String type,java.time.Instant createdAt,java.time.Instant updatedAt,Map<String,Object> payload){}
}
