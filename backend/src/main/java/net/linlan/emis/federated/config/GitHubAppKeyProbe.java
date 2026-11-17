package net.linlan.emis.federated.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/**
 * GitHub App 私钥只读探针：仅记录布尔状态，不读取内容到日志、不阻断启动、不参与登录。
 */
@Component
@Order(30)
public class GitHubAppKeyProbe implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(GitHubAppKeyProbe.class);

    private final FederatedProperties props;
    private volatile boolean loaded;

    public GitHubAppKeyProbe(FederatedProperties props) {
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        String path = props.getGithub().getPrivateKeyPath();
        if (path == null || path.isBlank()) {
            loaded = false;
            return;
        }
        try {
            byte[] content = Files.readAllBytes(Path.of(path).toRealPath(LinkOption.NOFOLLOW_LINKS));
            String text = new String(content);
            loaded = text.contains("-----BEGIN");
        } catch (Exception ex) {
            loaded = false;
            log.warn("GitHub App private key unavailable (not used by v1.1.0 login)");
        }
    }

    public boolean isLoaded() {
        return loaded;
    }
}