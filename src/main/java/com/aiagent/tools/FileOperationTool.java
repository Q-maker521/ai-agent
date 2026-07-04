package com.aiagent.tools;

import cn.hutool.core.io.FileUtil;
import com.aiagent.constant.FileConstant;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 文件操作工具类（提供文件读写功能）
 */
public class FileOperationTool {

    private final String FILE_DIR = FileConstant.FILE_SAVE_DIR + "/file";

    @Tool(description = """
            Read the content of a previously written file.

            WHEN TO USE: When you need to retrieve data that was saved in a previous
            step, or when the user asks you to read a specific file.

            Returns the file content as a UTF-8 string. Returns an error message if
            the file does not exist or cannot be read.
            """)
    public String readFile(@ToolParam(description = "Name of a file to read") String fileName) {
        String filePath = FILE_DIR + "/" + fileName;
        try {
            return FileUtil.readUtf8String(filePath);
        } catch (Exception e) {
            return "Error reading file: " + e.getMessage();
        }
    }

    @Tool(description = """
            Write content to a file for persistent storage.

            WHEN TO USE: When tool execution results, search findings, or generated
            content need to be saved to disk for later use or for the user to access.

            Files are saved in the workspace file directory. Use descriptive file names
            with appropriate extensions (e.g., "search_results.txt", "summary.md").

            Returns a success message with the file path, or an error message on failure.
            """)
    public String writeFile(@ToolParam(description = "Name of the file to write") String fileName,
                            @ToolParam(description = "Content to write to the file") String content
    ) {
        String filePath = FILE_DIR + "/" + fileName;

        try {
            // 创建目录
            FileUtil.mkdir(FILE_DIR);
            FileUtil.writeUtf8String(content, filePath);
            return "File written successfully to: " + filePath;
        } catch (Exception e) {
            return "Error writing to file: " + e.getMessage();
        }
    }
}
