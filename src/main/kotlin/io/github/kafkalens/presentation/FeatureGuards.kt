package io.github.kafkalens.presentation

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class DestructiveOpsDisabled : ResponseStatusException(
    HttpStatus.FORBIDDEN,
    "Destructive topic ops (delete / add partitions) are disabled. " +
        "Set TOPICOPS_ALLOWDESTRUCTIVE=true (or topic-ops.allow-destructive: true) and restart.",
)
