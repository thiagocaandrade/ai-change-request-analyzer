from graph.state import initial_state


def test_initial_state_has_all_sections():
    state = initial_state(request_id="req-1", text="Alterar desconto", trace_id="trace-1")
    assert set(state) == {
        "trace_id",
        "change_request",
        "classification",
        "retrieved_documents",
        "code_findings",
        "historical_findings",
        "impact_findings",
        "risk_assessment",
        "security_assessment",
        "test_plan",
        "approval_required",
        "approval_status",
        "final_result",
        "errors",
        "iteration_count",
    }


def test_initial_state_collections_empty_and_counter_zero():
    state = initial_state(request_id="req-2", text="Alterar frete", trace_id="trace-2")
    assert state["retrieved_documents"] == []
    assert state["code_findings"] == []
    assert state["historical_findings"] == []
    assert state["impact_findings"] == []
    assert state["test_plan"] == []
    assert state["errors"] == []
    assert state["security_assessment"] == {"detected": False, "events": []}
    assert state["iteration_count"] == 0


def test_initial_state_keeps_request_fields():
    state = initial_state(request_id="req-3", text="  Alterar desconto VIP  ", trace_id="trace-3")
    assert state["trace_id"] == "trace-3"
    assert state["change_request"]["request_id"] == "req-3"
    assert state["change_request"]["text"] == "Alterar desconto VIP"
    assert state["approval_required"] is False
    assert state["approval_status"] is None
