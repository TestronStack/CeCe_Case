package com.example.demo.controller;

import com.example.demo.auth.UserContext;
import com.example.demo.common.ApiResponse;
import com.example.demo.entity.GenerationTaskEntity;
import com.example.demo.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private static final ScheduledExecutorService POLLER = Executors.newScheduledThreadPool(4);

    private final TaskService taskService;

    @GetMapping
    public ApiResponse<List<GenerationTaskEntity>> list(@RequestParam(required = false) Long projectId,
                                                        @RequestParam(required = false) String status,
                                                        @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(taskService.list(UserContext.userId(), projectId, status, limit));
    }

    @GetMapping("/{id}")
    public ApiResponse<GenerationTaskEntity> get(@PathVariable Long id) {
        return ApiResponse.ok(taskService.get(UserContext.userId(), id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        taskService.delete(UserContext.userId(), id);
        return ApiResponse.ok("记录已删除", null);
    }

    /**
     * 真实进度推送：轮询任务表，状态变化即推送给前端，任务结束自动关闭连接。
     * EventSource 无法带请求头，token 通过 query 参数传递（拦截器已兼容）。
     */
    @GetMapping(value = "/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable Long id) {
        Long userId = UserContext.userId();
        taskService.get(userId, id);
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        long deadline = System.currentTimeMillis() + 30 * 60 * 1000L;
        int[] lastProgress = {-1};
        String[] lastStatus = {""};

        Runnable poll = () -> {
            try {
                GenerationTaskEntity task = taskService.get(userId, id);
                boolean changed = !String.valueOf(task.getProgress()).equals(String.valueOf(lastProgress[0]))
                        || !task.getStatus().equals(lastStatus[0]);
                if (changed) {
                    lastProgress[0] = task.getProgress();
                    lastStatus[0] = task.getStatus();
                    emitter.send(SseEmitter.event().name("progress").data(Map.of(
                            "id", task.getId(),
                            "status", task.getStatus(),
                            "progress", task.getProgress(),
                            "stage", task.getStage() == null ? "" : task.getStage(),
                            "message", task.getMessage() == null ? "" : task.getMessage(),
                            "batchId", task.getBatchId() == null ? 0 : task.getBatchId(),
                            "durationMs", task.getDurationMs() == null ? 0 : task.getDurationMs()
                    ), MediaType.APPLICATION_JSON));
                }
                if ("SUCCESS".equals(task.getStatus()) || "FAILED".equals(task.getStatus())
                        || System.currentTimeMillis() > deadline) {
                    emitter.complete();
                }
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        };
        POLLER.scheduleAtFixedRate(poll, 0, 600, TimeUnit.MILLISECONDS);
        emitter.onCompletion(() -> {
        });
        emitter.onTimeout(emitter::complete);
        return emitter;
    }
}
