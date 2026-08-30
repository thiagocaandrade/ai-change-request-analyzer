package com.ai.change.request.analyzer.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepoAccessPolicyTest {

  @TempDir Path root;

  @Test
  void resolvesPathInsideRoot() {
    RepoAccessPolicy policy = new RepoAccessPolicy(root.toString());

    Path resolved = policy.resolveInside("src/main/java/Foo.java");

    assertThat(resolved).isEqualTo(root.resolve("src/main/java/Foo.java").normalize());
  }

  @Test
  void rejectsPathTraversal() {
    RepoAccessPolicy policy = new RepoAccessPolicy(root.toString());

    assertThatThrownBy(() -> policy.resolveInside("../outside.txt"))
        .isInstanceOf(ToolAccessException.class)
        .satisfies(
            e ->
                assertThat(((ToolAccessException) e).getCode())
                    .isEqualTo(RepoAccessPolicy.ERROR_PATH_TRAVERSAL));
  }

  @Test
  void rejectsAbsolutePathOutsideRoot() {
    RepoAccessPolicy policy = new RepoAccessPolicy(root.toString());

    assertThatThrownBy(() -> policy.resolveInside("/etc/passwd"))
        .isInstanceOf(ToolAccessException.class)
        .satisfies(
            e ->
                assertThat(((ToolAccessException) e).getCode())
                    .isEqualTo(RepoAccessPolicy.ERROR_OUTSIDE_ROOT));
  }

  @Test
  void rejectsEmptyPath() {
    RepoAccessPolicy policy = new RepoAccessPolicy(root.toString());

    assertThatThrownBy(() -> policy.resolveInside("   "))
        .isInstanceOf(ToolAccessException.class)
        .satisfies(
            e ->
                assertThat(((ToolAccessException) e).getCode())
                    .isEqualTo(RepoAccessPolicy.ERROR_EMPTY_PATH));
  }

  @Test
  void defaultsToWorkingDirectoryWhenNotConfigured() {
    RepoAccessPolicy policy = new RepoAccessPolicy(null);

    assertThat(policy.root()).isEqualTo(Path.of("").toAbsolutePath().normalize());
  }
}
