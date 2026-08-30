package com.ai.change.request.analyzer.web;

import com.ai.change.request.analyzer.domain.Approval;
import com.ai.change.request.analyzer.domain.ApprovalDecision;
import com.ai.change.request.analyzer.domain.ChangeRequest;
import com.ai.change.request.analyzer.domain.ChangeRequestRepository;
import com.ai.change.request.analyzer.web.ApprovalDtos.ApprovalRequest;
import com.ai.change.request.analyzer.web.GlobalExceptionHandler.ApprovalConflictException;
import com.ai.change.request.analyzer.web.GlobalExceptionHandler.ChangeRequestNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
 * Paginas Thymeleaf do analisador: formulario de solicitacao e resultado da analise (com decisao
 * humana). Nao duplica a orquestracao da API: delega aos beans dos controllers REST; erros de
 * pagina sao tratados aqui (HTML amigavel, nunca JSON). Todo dado nao confiavel e renderizado com
 * {@code th:text} (escapado).
 */
@Controller
public class WebController {

  private static final String NOT_FOUND_VIEW = "not-found";

  private final ChangeRequestController changeRequestController;
  private final ChangeRequestRepository repository;

  public WebController(
      ChangeRequestController changeRequestController, ChangeRequestRepository repository) {
    this.changeRequestController = changeRequestController;
    this.repository = repository;
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
      changeRequestController.approve(id, new ApprovalRequest(approver.trim(), parsed), httpRequest);
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
}
