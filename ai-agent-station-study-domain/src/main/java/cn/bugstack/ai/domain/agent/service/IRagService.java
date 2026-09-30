package cn.bugstack.ai.domain.agent.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 知识库接口服务
 */
public interface IRagService {

    void storeRagFile(String name, String tag, List<MultipartFile> files);

}
