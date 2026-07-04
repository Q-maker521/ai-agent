package com.aiagent.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.aiagent.constant.FileConstant;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.File;

/**
 * 资源下载工具 — 从指定 URL 下载文件到本地。
 *
 * <p>模拟浏览器 User-Agent，设置超时，支持 HTTP/HTTPS 资源下载。
 */
public class ResourceDownloadTool {

    private static final int TIMEOUT_MS = 30_000;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";

    @Tool(description = """
            Download a file from a given URL and save it to the local filesystem.

            WHEN TO USE: When the user asks to download a file, image, document, or
            other resource from a specific URL. Also useful for saving search results
            or generated content from external services.

            DIFFERS FROM web_scrape: web_scrape extracts and returns text content from
            a web page. download_resource saves the raw file (any format) to disk. Use
            web_scrape to READ content, use download_resource to SAVE files.

            LIMITATIONS: HTTP/HTTPS only. 30-second timeout. Follows redirects.
            Returns a success message with the file path, or an error on failure.
            """)
    public String downloadResource(
            @ToolParam(description = "URL of the resource to download") String url,
            @ToolParam(description = "Name of the file to save the downloaded resource") String fileName) {
        String fileDir = FileConstant.FILE_SAVE_DIR + "/download";
        String filePath = fileDir + "/" + fileName;
        try {
            FileUtil.mkdir(fileDir);
            File targetFile = new File(filePath);

            HttpResponse response = HttpRequest.get(url)
                    .header("User-Agent", USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .setFollowRedirects(true)
                    .execute();

            if (!response.isOk()) {
                return "Download failed: HTTP " + response.getStatus()
                        + " from " + url;
            }

            response.writeBody(targetFile);
            long fileSize = targetFile.length();
            String sizeStr = fileSize < 1024
                    ? fileSize + " B"
                    : fileSize < 1024 * 1024
                        ? String.format("%.1f KB", fileSize / 1024.0)
                        : String.format("%.1f MB", fileSize / (1024.0 * 1024.0));

            return "Resource downloaded successfully to: " + filePath + " (" + sizeStr + ")";

        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("timeout")) {
                return "Download timed out after " + (TIMEOUT_MS / 1000)
                        + "s: " + url + ". The server may be slow or unreachable.";
            }
            return "Error downloading resource: " + e.getMessage();
        }
    }
}
