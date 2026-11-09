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
		System.out.println("\n");
		System.out.println("╔════════════════════════════════════════════════════════════════════════════╗");
		System.out.println("║                  🎉 AI测试用例生成工具已启动！                                  ║");
		System.out.println("╠════════════════════════════════════════════════════════════════════════════╣");
		System.out.println("║  📍 访问地址: http://localhost:8819/api/                                    ║");
		//System.out.println("║  📖 Swagger文档: http://localhost:8819/swagger-ui.html            ║");
		System.out.println("║                                                                           ║");
		System.out.println("║  📌 功能说明:                                                              ║");
		System.out.println("║     • Tab1: 根据需求文档生成测试用例                                          ║" );
		System.out.println("║       .上传的需求文档支持.DOCX/.PDF/.MD/.TXT等文本类型                        ║" );
		System.out.println("║       .可以自主切换AI大模型，发挥各个模型的特色                                 ║");
		System.out.println("║       .可以选取指定页数/章节/关键词来生成对应的测试用例，当需求文档过大的时候可以用上   ║");
		System.out.println("║       .可以自定义测试模板，针对不同公司模板不同，生成对应的测试用例                  ║");
		System.out.println("║       .输出测试用例格式多样，支持Excel/CSV/JSON/Markdown                      ║");
		System.out.println("║       .可指定生成测试用例的路径                                              ║");
		//System.out.println("║                                                              ║");
		System.out.println("║     • Tab2: 根据接口文档生成自动化脚本                                       ║");
		System.out.println("║       .上传的接口文档支持.DOCX/.PDF/.JSON/.YAML等文本类型                     ║" );
		System.out.println("║       .可以自主切换AI大模型，发挥各个模型的特色                                 ║");
		System.out.println("║       .可以选取指定页数来生成对应的测试用例，当接口文档过大的时候可以用上             ║");
		System.out.println("║       .可以自主切换脚本语言(Java/Python)                                     ║");
		System.out.println("║       .可指定生成接口脚本的路径，自动打包成.zip文件                             ║");
		//System.out.println("║                                                              ║");
		System.out.println("║     • Tab3: 指定需求或接口，描述你的需求，生成测试用例                           ║");
		System.out.println("║       .可以自主选择输入的需求类型(需求描述/接口描述)                             ║" );
		System.out.println("║       .可以自主切换AI大模型，发挥各个模型的特色                                 ║");
		System.out.println("║       .自定义输入的内容，生成特定的测试用例以及自定义生成测试用例的数量              ║");
		System.out.println("║       .可以自定义测试模板，针对不同公司模板不同，生成对应的测试用例                  ║");
		System.out.println("║       .输出测试用例格式多样，支持Excel/CSV/JSON/Markdown                      ║");
		System.out.println("║       .可指定生成测试用例的路径                                              ║");

		System.out.println("╚══════════════════════════════════════════════════════════════════════════╝");
		System.out.println("\n");
	}
}