package com.adjuva.backend.service;

import com.adjuva.backend.handler.RunExecutor;
import com.adjuva.backend.model.entity.Conversation;
import com.adjuva.backend.mapper.ConversationMapper;
import com.adjuva.backend.model.entity.Run;
import com.adjuva.backend.mapper.RunMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class RunLauncher {

    private final ConversationMapper conversationMapper;
    private final RunMapper runMapper;
    private final ApplicationContext applicationContext;
    private final boolean enabled;
    private final ExecutorService executorService = Executors.newCachedThreadPool(
            new CustomizableThreadFactory("adjuva-run-"));
    private final Map<String, Process> processes = new ConcurrentHashMap<>();

    public RunLauncher(
            ConversationMapper conversationMapper,
            RunMapper runMapper,
            ApplicationContext applicationContext,
            @Value("${adjuva.executor.enabled:true}") boolean enabled
    ) {
        this.conversationMapper = conversationMapper;
        this.runMapper = runMapper;
        this.applicationContext = applicationContext;
        this.enabled = enabled;
    }

    public void launchAfterCommit(String runId) {
        if (!enabled) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    launch(runId);
                }
            });
            return;
        }
        launch(runId);
    }

    public void launch(String runId) {
        executorService.submit(() -> {
            Run run = runMapper.selectById(runId);
            if (run == null || !"running".equals(run.getLifecycle())) {
                return;
            }
            Conversation conversation = conversationMapper.selectById(run.getConversationId());
            if (conversation == null) {
                return;
            }
            RunExecutor executor = executorFor(run.getProvider());
            executor.execute(conversation, run);
        });
    }

    public void registerProcess(String runId, Process process) {
        processes.put(runId, process);
    }

    public void unregisterProcess(String runId) {
        processes.remove(runId);
    }

    public boolean terminateProcess(String runId) {
        Process process = processes.remove(runId);
        if (process == null) {
            return false;
        }
        process.destroy();
        return true;
    }

    private RunExecutor executorFor(String provider) {
        return applicationContext.getBeansOfType(RunExecutor.class).values().stream()
                .filter(executor -> executor.provider().equals(provider))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported provider: " + provider));
    }
}
