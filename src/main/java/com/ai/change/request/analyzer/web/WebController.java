package com.ai.change.request.analyzer.web;

import com.ai.change.request.analyzer.domain.Approval;
import com.ai.change.request.analyzer.domain.ApprovalDecision;
import com.ai.change.request.analyzer.domain.ChangeRequest;
import com.ai.change.request.analyzer.domain.ChangeRequestRepository;
import com.ai.change.request.analyzer.observability.TraceEvent;
import com.ai.change.request.analyzer.observability.TraceService;
import com.ai.change.request.analyzer.web.ApprovalDtos.ApprovalRequest;
import com.ai.change.request.analyzer.web.GlobalExceptionHandler.ApprovalConflictException;
import com.ai.change.request.analyzer.web.GlobalExceptionHandler.ChangeRequestNotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

/**
 * Paginas Thymeleaf do analisador: formulario de solicitacao, resultado da analise (com decisao
 * humana) e reconstrucao de execucao por trace_id. Nao duplica a orquestracao da API: delega aos
 * beans dos controllers REST e ao {@link TraceService}; erros de pagina sao tratados aqui (HTML
 * amigavel, nunca JSON). Todo dado nao confiavel e renderizado com {@code th:text} (escapado).
 */
@Controller
public class WebController {

  private static final String NOT_FOUND_VIEW = "not-found";

  private final ChangeRequestController changeRequestController;
  private final ChangeRequestRepository repository;
  private final TraceService traceService;
  private final ObjectMapper objectMapper;

  public WebController(
      ChangeRequestController changeRequestController,
      ChangeRequestRepository repository,
      TraceService traceService,
      ObjectMapper objectMapper) {
    this.changeRequestController = changeRequestController;
    this.repository = repository;
    this.traceService = traceService;
    this.objectMapper = objectMapper;
  }

  /** Formulario da solicitacao de alteracao. */
  public static class ChangeForm {
    private String text = "";

    public String getText() {
      return text;
    }

    public void setText(String text) {
      this.text = text;
    }
  }

  /** Evento da execucao com os documentos recuperados (parsed do detalhe), para a pagina. */
  public record TraceItem(TraceEvent event, List<RetrievedDocument> documents) {}

  /** Fonte de documento recuperado exibida na reconstrucao da execucao. */
  public record RetrievedDocument(String source, String documentId, Double score) {}

  @GetMapping("/")
  public String form(Model model) {
    model.addAttribute("form", new ChangeForm());
    return "index";
  }

  @PostMapping("/change-requests")
  public Object submit(
      @ModelAttribute("form") ChangeForm form,
      BindingResult bindingResult,
      HttpServletRequest httpRequest) {
    String text = form.getText() == null ? "" : form.getText().trim();
    if (text.isEmpty()) {
      bindingResult.rejectValue(
          "text", "text.blank", "Descreva a solicitação de alteração antes de enviar.");
      return "index";
    }
    ResponseEntity<ChangeRequestResponse> response =
        changeRequestController.create(new CreateChangeRequestRequest(text), httpRequest);
    ChangeRequestResponse body = response.getBody();
    return redirectSeeOther("/requests/" + body.id());
  }

  @GetMapping("/requests/{id}")
  public String result(
      @PathVariable UUID id,
      @ModelAttribute("error") String error,
      Model model,
      HttpServletResponse response) {
    ChangeRequestResponse request;
    try {
      request = changeRequestController.get(id);
    } catch (ChangeRequestNotFoundException e) {
      return notFound(model, response, "Solicitação não encontrada.");
    }
    AnalysisResponse analysis = null;
    try {
      analysis = changeRequestController.getAnalysis(id);
    } catch (GlobalExceptionHandler.AnalysisNotFoundException e) {
      // Pagina renderiza o status da solicitacao sem a analise (ex.: PENDING/FAILED).
    }
    Approval approval = repository.findById(id).map(ChangeRequest::getApproval).orElse(null);
    model.addAttribute("request", request);
    model.addAttribute("analysis", analysis);
    model.addAttribute("approval", approval);
    model.addAttribute("error", error);
    return "result";
  }

  @PostMapping("/requests/{id}/approval")
  public Object approve(
      @PathVariable UUID id,
      @RequestParam String approver,
      @RequestParam String decision,
      RedirectAttributes attributes,
      HttpServletRequest httpRequest,
      Model model,
      HttpServletResponse response) {
    ApprovalDecision parsed = null;
    if (decision != null) {
      try {
        parsed = ApprovalDecision.valueOf(decision.trim().toUpperCase());
      } catch (IllegalArgumentException e) {
        parsed = null;
      }
    }
    if (parsed == null || approver == null || approver.isBlank()) {
      attributes.addFlashAttribute(
          "error", "Decisão inválida: informe o aprovador e escolha APPROVED ou REJECTED.");
      return redirectSeeOther("/requests/" + id);
    }
    try {
      changeRequestController.approve(
          id, new ApprovalRequest(approver.trim(), parsed), httpRequest);
    } catch (ApprovalConflictException e) {
      attributes.addFlashAttribute(
          "error",
          "A decisão já foi registrada ou a aprovação não é exigida para esta solicitação.");
    } catch (ChangeRequestNotFoundException e) {
      return notFound(model, response, "Solicitação não encontrada.");
    }
    return redirectSeeOther("/requests/" + id);
  }

  @PostMapping("/traces")
  public Object lookupTrace(@RequestParam String traceId, RedirectAttributes attributes) {
    String id = traceId == null ? "" : traceId.trim();
    if (id.isEmpty()) {
      attributes.addFlashAttribute("error", "Informe o trace_id da execução.");
      return redirectSeeOther("/");
    }
    return redirectSeeOther("/traces/" + id);
  }

  @GetMapping("/traces/{traceId}")
  public String trace(@PathVariable String traceId, Model model, HttpServletResponse response) {
    List<TraceEvent> events = traceService.findByTraceId(traceId);
    if (events.isEmpty()) {
      return notFound(model, response, "Trace não encontrado: nenhum evento registrado.");
    }
    List<TraceItem> items =
        events.stream().map(e -> new TraceItem(e, parseDocuments(e.getDetail()))).toList();
    model.addAttribute("traceId", traceId);
    model.addAttribute("items", items);
    model.addAttribute(
        "hasDocuments", items.stream().anyMatch(item -> !item.documents().isEmpty()));
    return "trace";
  }

  private String notFound(Model model, HttpServletResponse response, String message) {
    response.setStatus(HttpStatus.NOT_FOUND.value());
    model.addAttribute("message", message);
    return NOT_FOUND_VIEW;
  }

  private RedirectView redirectSeeOther(String url) {
    RedirectView view = new RedirectView(url, true);
    view.setStatusCode(HttpStatus.SEE_OTHER);
    return view;
  }

  private List<RetrievedDocument> parseDocuments(String detail) {
    if (detail == null || detail.isBlank()) {
      return List.of();
    }
    try {
      JsonNode root = objectMapper.readTree(detail);
      if (!root.isArray()) {
        return List.of();
      }
      List<RetrievedDocument> documents = new ArrayList<>();
      for (JsonNode node : root) {
        documents.add(
            new RetrievedDocument(
                textOrNull(node, "source"),
                textOrNull(node, "document_id"),
                node.path("score").isNumber() ? node.path("score").asDouble() : null));
      }
      return List.copyOf(documents);
    } catch (JsonProcessingException e) {
      return List.of();
    }
  }

  private String textOrNull(JsonNode node, String field) {
    JsonNode value = node.path(field);
    return value.isNull() || value.isMissingNode() ? null : value.asText();
  }
}
