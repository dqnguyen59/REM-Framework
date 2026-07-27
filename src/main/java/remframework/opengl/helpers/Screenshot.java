package remframework.opengl.helpers;

import lombok.Getter;
import lombok.Setter;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.system.MemoryUtil;
import remframework.settings.AppSettings;
import remframework.helpers.Utils;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

@Getter
@Setter
public class Screenshot {

    private static final AppSettings appSettings = AppSettings.getInstance();

    /**
     * Use this function when a certain part during the rendering
     * should be included in the screenshot or not.
     */
    private static boolean       takingScreenShot;
    private static int           screenshotBackgroundTransparency;
    private static int           screenshotIndexX;
    private static int           screenshotIndexY;
    private static int           screenshotIndexMaxX;
    private static int           screenshotIndexMaxY;
    private static BufferedImage imgScreenshot;

    /**
     * To take a screenshot, call this method once.<br/>
     * <br/>
     * See also method {@link #updateScreenshotViewport()}.<br/>
     * See also method {@link #takeScreenShot()}.<br/>
     * <br/>
     *
     * @param zoomWidth              Zoom in the scene width by a factor
     * @param zoomHeight             Zoom in the scene height by a factor
     * @param backgroundTransparency 0x00 is transparent and 0xFF is opaque
     */
    public static void prepareScreenshot(int zoomWidth, int zoomHeight, int backgroundTransparency) {
        int screenshotWidth  = appSettings.getWindowWidth() * zoomWidth;
        int screenshotHeight = appSettings.getWindowHeight() * zoomHeight;

        screenshotIndexX                 = 0;
        screenshotIndexY                 = 0;
        screenshotIndexMaxX              = zoomWidth;
        screenshotIndexMaxY              = zoomHeight;
        screenshotBackgroundTransparency = backgroundTransparency;
        setTakingScreenShot(true);
        imgScreenshot = new BufferedImage(screenshotWidth, screenshotHeight, BufferedImage.TYPE_INT_ARGB);
    }

    /**
     * This method adjust the View port and should be called before rendering.<br/>
     * <br/>
     * See also method .<br/>
     * See also method {@link #takeScreenShot()}.<br/>
     *
     */
    public static void updateScreenshotViewport() {
        int x = -screenshotIndexX * appSettings.getWindowWidth();
        int y = -screenshotIndexY * appSettings.getWindowHeight();
        GL11C.glViewport(x, y, appSettings.getDisplayWidth() * screenshotIndexMaxX,
                         appSettings.getDisplayHeight() * screenshotIndexMaxY);
    }

    /**
     * This method takes the actual screenshot and should be called after rendering.<br/>
     * <br/>
     * See also method .<br/>
     * See also method {@link #updateScreenshotViewport()}.<br/>
     *
     */
    public static void takeScreenShot() {
        Screenshot.screenshot(0, 0, appSettings.getWindowWidth(), appSettings.getWindowHeight(),
                              screenshotBackgroundTransparency,
                              imgScreenshot,
                              screenshotIndexX * appSettings.getWindowWidth(),
                              (screenshotIndexMaxY - screenshotIndexY - 1) * appSettings.getWindowHeight());

        if (screenshotIndexY < screenshotIndexMaxY) {
            screenshotIndexX++;
            if (screenshotIndexX >= screenshotIndexMaxX) {
                screenshotIndexX = 0;
                screenshotIndexY++;
            }
        }

        if (screenshotIndexY >= screenshotIndexMaxY) {
            new Thread(new Runnable() {

                @Override
                public void run() {
                    saveToImageFile("Screenshot-" + Utils.getFileDatetimeAsString() + ".png", imgScreenshot);
                }
            }).start();
            setTakingScreenShot(false);
        }
    }

    private static void screenshot(
            int x, int y, int width, int height, int transparency,
            BufferedImage dstimage, int imageOffsetX, int imageOffsetY) {
        int        imageSize = width * height;
        ByteBuffer buffer    = MemoryUtil.memAlloc(imageSize * 4);

        GL11C.glReadPixels(x, y, width, height, GL20C.GL_RGBA, GL20C.GL_UNSIGNED_BYTE, buffer);

        //BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        for (int j = height - 1; j >= 0; j--) {
            for (int i = 0; i < width; i++) {
                int n = buffer.getInt();
                n = Integer.reverseBytes(n);
                n = Integer.rotateRight(n, 8);
                n = n | transparency << 24;
                dstimage.setRGB(imageOffsetX + i, imageOffsetY + j, n);
            }
        }

        MemoryUtil.memFree(buffer);
    }

    public static void saveToImageFile(String filename, BufferedImage bufImage) {
        if (bufImage != null) {
            File file = new File(filename);

            try {
                javax.imageio.ImageIO.write(bufImage, "png", file);
                javax.imageio.ImageIO.getReaderFormatNames();
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static boolean isTakingScreenShot() {
        return takingScreenShot;
    }

    public static void setTakingScreenShot(boolean takingScreenShot) {
        Screenshot.takingScreenShot = takingScreenShot;
    }
}
