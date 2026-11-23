package cc.mrbird.febs.cos.controller;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class MiniProgramControllerTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void uploadUsesConfiguredDirectoryAndGeneratesSafeFilenameFromImageFormat() throws Exception {
        MiniProgramController controller = new MiniProgramController(null);
        ReflectionTestUtils.setField(controller, "uploadPath", temporary.getRoot().getAbsolutePath());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(4, 2, BufferedImage.TYPE_INT_RGB), "png", bytes);
        String filename = (String) controller.upload(new MockMultipartFile("image", "../../name.html", "image/png", bytes.toByteArray())).get("data");
        assertTrue(filename, filename.matches("banner-[a-f0-9]{32}\\.png"));
        Path saved = temporary.getRoot().toPath().resolve(filename);
        assertArrayEquals(bytes.toByteArray(), Files.readAllBytes(saved));
    }

    @Test
    public void uploadRejectsDisguisedNonImageEmptyAndOversizeFiles() throws Exception {
        MiniProgramController controller = new MiniProgramController(null);
        ReflectionTestUtils.setField(controller, "uploadPath", temporary.getRoot().getAbsolutePath());
        for (byte[] content : new byte[][] {"<script>alert(1)</script>".getBytes("UTF-8"), new byte[0], new byte[10 * 1024 * 1024 + 1]}) {
            try {
                controller.upload(new MockMultipartFile("image", "bad.png", "image/png", content));
                fail("Expected invalid image rejection");
            } catch (IllegalArgumentException expected) { assertNotNull(expected.getMessage()); }
        }
        assertEquals(0, temporary.getRoot().list().length);
    }

    @Test
    public void uploadAcceptsValidImageAtTenMegabyteLimit() throws Exception {
        MiniProgramController controller = new MiniProgramController(null);
        ReflectionTestUtils.setField(controller, "uploadPath", temporary.getRoot().getAbsolutePath());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(4, 2, BufferedImage.TYPE_INT_RGB), "png", bytes);
        byte[] content = java.util.Arrays.copyOf(bytes.toByteArray(), 10 * 1024 * 1024);
        String filename = (String) controller.upload(new MockMultipartFile("image", "limit.png", "image/png", content)).get("data");
        assertEquals(content.length, Files.size(temporary.getRoot().toPath().resolve(filename)));
    }
}
