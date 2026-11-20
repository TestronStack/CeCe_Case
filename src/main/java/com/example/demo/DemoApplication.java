package com.example.demo;

import com.example.demo.service.AiService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

@SpringBootApplication
public class DemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}

	@EventListener(ApplicationReadyEvent.class)
	public void onApplicationReady() {
		AiService.initModel();
		showStartupInfo();
	}

	private void showStartupInfo() {
		System.out.println();
		System.out.println("============================================================");
		System.out.println("  CeCe AI Testing Platform 已启动");
		System.out.println("  访问地址 : http://localhost:8819/");
		System.out.println("  演示账号 : admin / 123456");
		System.out.println("  功能菜单 : 工作台 | 需求文档生成 | 接口用例生成 | 自动化脚本");
		System.out.println("             自然语言生成 | 用例库 | 文档库 | 任务记录");
		System.out.println("             项目管理 | 模板管理 | 设置");
		System.out.println("============================================================");
		System.out.println();
	}
}