package cn.bugstack.ai.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 知识库更新请求 DTO
 *
 * @author bugstack.cn
 * @description 知识库更新请求数据传输对象
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RagUpdateRequestDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 知识库ID
     */
    private String ragId;

    /**
     * 更新原因
     */
    private String updateReason;

    /**
     * 上传的文件列表
     */
    private List<MultipartFile> files;

}
