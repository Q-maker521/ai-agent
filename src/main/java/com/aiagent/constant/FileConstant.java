package com.aiagent.constant;

/**
 * 文件常量
 */
public interface FileConstant {

    /**
     * 文件保存目录
     */
    String FILE_SAVE_DIR = System.getProperty("user.dir") + "/tmp";

    /**
     * 用户上传的知识库文档目录
     */
    String USER_UPLOADS_DIR = System.getProperty("user.dir") + "/docs/user-uploads";
}
