import pytest

from graph.builder import build_graph


def test_graph_runs_end_to_end():
    graph = build_graph()
    result = graph.invoke({"request_id": "req-1", "text": "  Alterar desconto VIP  "})
    assert result["status"] == "completed"
    assert result["request_id"] == "req-1"
    assert result["text"] == "Alterar desconto VIP"
    assert result["result"]["processed_text"] == "Alterar desconto VIP"


def test_graph_rejects_empty_text():
    graph = build_graph()
    with pytest.raises(ValueError):
        graph.invoke({"request_id": "req-2", "text": "   "})
