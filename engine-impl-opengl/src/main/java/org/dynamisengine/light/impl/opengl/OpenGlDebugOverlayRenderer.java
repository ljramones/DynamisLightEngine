package org.dynamisengine.light.impl.opengl;

import org.dynamisengine.ui.debug.builder.DebugViewSnapshot;
import org.dynamisengine.ui.debug.model.*;
import org.dynamisengine.ui.debug.render.DebugOverlayRenderer;

import java.util.List;

import static org.lwjgl.opengl.GL11.GL_SCISSOR_TEST;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glScissor;

/**
 * Minimal OpenGL implementation of {@link DebugOverlayRenderer}.
 *
 * <p>Uses the proving module's {@link OpenGlTextRenderer} for text and filled rects.
 * This is a Phase 1 renderer — layout is simple column-based positioning,
 * not a full layout engine.
 *
 * <p>Lives in the proving module because it depends on LWJGL. A production
 * renderer would live in DynamisLightEngine.
 */
public final class OpenGlDebugOverlayRenderer implements DebugOverlayRenderer {

    // Layout constants
    private static final float PANEL_WIDTH = 280f;
    private static final float PANEL_PADDING = 8f;
    private static final float ROW_HEIGHT = 14f;
    private static final float PANEL_GAP = 6f;
    private static final float COLUMN_GAP = 10f;
    private static final float TEXT_SCALE = 1.8f;
    private static final float HEADER_SCALE = 2.0f;
    private static final float TREND_HEIGHT = 24f;
    private static final float COMPACT_HEIGHT = 30f;
    private static final float MAX_ALERT_HEIGHT = 80f;

    // Colors
    private static final float[] BG_NORMAL  = {0.0f, 0.0f, 0.0f, 0.75f};
    private static final float[] BG_WARNING = {0.15f, 0.12f, 0.0f, 0.8f};
    private static final float[] BG_ERROR   = {0.2f, 0.05f, 0.05f, 0.85f};
    private static final float[] TEXT_NORMAL  = {0.7f, 0.8f, 0.7f};
    private static final float[] TEXT_WARNING = {1.0f, 0.8f, 0.2f};
    private static final float[] TEXT_ERROR   = {1.0f, 0.3f, 0.3f};
    private static final float[] TEXT_HEADER  = {0.3f, 1.0f, 0.6f};
    private static final float[] TEXT_DIM     = {0.5f, 0.55f, 0.5f};
    private static final float[] FLAG_OK      = {0.4f, 0.5f, 0.4f};
    private static final float[] FLAG_ACTIVE  = {0.3f, 0.9f, 0.3f};
    private static final float[] FLAG_WARN    = {0.9f, 0.7f, 0.2f};
    private static final float[] FLAG_ERR     = {0.9f, 0.2f, 0.2f};

    private final OpenGlTextRenderer textRenderer;
    private int screenW, screenH;

    OpenGlDebugOverlayRenderer(OpenGlTextRenderer textRenderer) {
        this.textRenderer = textRenderer;
    }

    /**
     * Renders all panels with simple two-column layout.
     * TOP panels span the full width at the top.
     * GRID panels flow in two columns.
     * BOTTOM panels go at the bottom.
     */
    @Override
    public void renderPanels(List<DebugOverlayPanel> panels, int screenW, int screenH) {
        this.screenW = screenW;
        this.screenH = screenH;

        org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
        beginOverlay();

        float startX = 8f;
        float y = 8f;

        // TOP panels (full width)
        for (var panel : panels) {
            if (panel.region() != PanelRegion.TOP) continue;
            float h = panelHeight(panel);
            LayoutBox box = new LayoutBox(startX, y, PANEL_WIDTH * 2 + COLUMN_GAP, h);
            drawPanel(panel, box);
            y += h + PANEL_GAP;
        }

        // GRID panels (two columns)
        float col1X = startX;
        float col2X = startX + PANEL_WIDTH + COLUMN_GAP;
        float col1Y = y, col2Y = y;
        boolean useCol1 = true;

        for (var panel : panels) {
            if (panel.region() != PanelRegion.GRID) continue;
            float h = panelHeight(panel);
            if (useCol1) {
                drawPanel(panel, new LayoutBox(col1X, col1Y, PANEL_WIDTH, h));
                col1Y += h + PANEL_GAP;
            } else {
                drawPanel(panel, new LayoutBox(col2X, col2Y, PANEL_WIDTH, h));
                col2Y += h + PANEL_GAP;
            }
            useCol1 = !useCol1;
        }

        // BOTTOM panels
        float bottomY = Math.max(col1Y, col2Y) + PANEL_GAP;
        for (var panel : panels) {
            if (panel.region() != PanelRegion.BOTTOM) continue;
            float h = panelHeight(panel);
            drawPanel(panel, new LayoutBox(startX, bottomY, PANEL_WIDTH * 2 + COLUMN_GAP, h));
            bottomY += h + PANEL_GAP;
        }

        endOverlay();
    }

    @Override
    public void beginOverlay() {
        textRenderer.beginFrame(screenW, screenH);
    }

    @Override
    public void drawPanel(DebugOverlayPanel panel, LayoutBox box) {
        boolean compact = isEmpty(panel);

        // Background — dimmer for empty panels
        float[] bg;
        if (compact) {
            bg = new float[]{0.0f, 0.0f, 0.0f, 0.4f};
        } else {
            bg = switch (panel.severity()) {
                case WARNING -> BG_WARNING;
                case ERROR -> BG_ERROR;
                default -> BG_NORMAL;
            };
        }
        textRenderer.drawRect(box.x(), box.y(), box.width(), box.height(),
            bg[0], bg[1], bg[2], bg[3], screenW, screenH);

        // Highlighted panels get a brighter left border
        if (panel.highlighted()) {
            textRenderer.drawRect(box.x(), box.y(), 3f, box.height(),
                1f, 0.8f, 0.2f, 1f, screenW, screenH);
        }

        // Clip content to panel bounds (OpenGL scissor: Y is bottom-up)
        glEnable(GL_SCISSOR_TEST);
        glScissor((int) box.x(), screenH - (int)(box.y() + box.height()),
            (int) box.width(), (int) box.height());

        float x = box.x() + PANEL_PADDING;
        float y = box.y() + (compact ? 4f : PANEL_PADDING);

        // Title
        if (compact) {
            textRenderer.drawText(panel.title() + " - no data", x, y, TEXT_SCALE,
                TEXT_DIM[0], TEXT_DIM[1], TEXT_DIM[2], screenW, screenH);
            glDisable(GL_SCISSOR_TEST);
            return;
        }

        float[] titleColor = panel.highlighted() ? TEXT_WARNING : TEXT_HEADER;
        textRenderer.drawText(panel.title(), x, y, HEADER_SCALE,
            titleColor[0], titleColor[1], titleColor[2], screenW, screenH);
        y += ROW_HEIGHT + 2;

        // Rows
        for (var row : panel.rows()) {
            drawRow(row, x, y, box.width() - PANEL_PADDING * 2);
            y += ROW_HEIGHT;
        }

        // Flags
        if (!panel.flags().isEmpty()) {
            y += 2;
            for (var flag : panel.flags()) {
                drawFlag(flag, x, y);
                y += ROW_HEIGHT;
            }
        }

        // Trends (sparklines)
        if (!panel.trends().isEmpty()) {
            y += 4;
            float trendHeight = TREND_HEIGHT;
            float trendWidth = box.width() - PANEL_PADDING * 2;
            for (var trend : panel.trends()) {
                // Label
                textRenderer.drawText(trend.metricName(), x, y, TEXT_SCALE * 0.75f,
                    TEXT_DIM[0], TEXT_DIM[1], TEXT_DIM[2], screenW, screenH);
                y += ROW_HEIGHT * 0.7f;

                // Sparkline
                drawTrend(trend, new LayoutBox(x, y, trendWidth, trendHeight));
                y += trendHeight + 2;
            }
        }

        glDisable(GL_SCISSOR_TEST);
    }

    @Override
    public void drawRow(DebugOverlayRow row, float x, float y, float width) {
        float[] color = switch (row.severity()) {
            case WARNING -> TEXT_WARNING;
            case ERROR -> TEXT_ERROR;
            default -> TEXT_NORMAL;
        };
        String line = row.label() + ": " + row.value();
        line = truncate(line, width);
        textRenderer.drawText(line, x, y, TEXT_SCALE,
            color[0], color[1], color[2], screenW, screenH);
    }

    /** Truncate text to fit within pixel width. STBEasyFont ≈ 8px per char at scale 1.8. */
    private static String truncate(String text, float widthPx) {
        int maxChars = Math.max(5, (int) (widthPx / (8 * TEXT_SCALE / 2)));
        if (text.length() <= maxChars) return text;
        return text.substring(0, maxChars - 2) + "..";
    }

    @Override
    public void drawFlag(DebugFlagView flag, float x, float y) {
        float[] color = switch (flag.state()) {
            case ACTIVE -> FLAG_ACTIVE;
            case WARNING -> FLAG_WARN;
            case ERROR -> FLAG_ERR;
            default -> FLAG_OK;
        };
        String text = "[" + flag.state().name() + "] " + flag.name();
        textRenderer.drawText(text, x, y, TEXT_SCALE * 0.9f,
            color[0], color[1], color[2], screenW, screenH);
    }

    @Override
    public void drawTrend(DebugMiniTrend trend, LayoutBox box) {
        var values = trend.values();
        if (values.size() < 2) return;

        // Background
        textRenderer.drawRect(box.x(), box.y(), box.width(), box.height(),
            0.05f, 0.05f, 0.08f, 0.9f, screenW, screenH);

        // Normalize values to box height
        double range = trend.max() - trend.min();
        if (range < 0.0001) range = 1.0; // avoid division by zero

        float stepX = box.width() / (values.size() - 1);

        // Draw sparkline segments
        for (int i = 1; i < values.size(); i++) {
            float x0 = box.x() + (i - 1) * stepX;
            float x1 = box.x() + i * stepX;
            float norm0 = (float) ((values.get(i - 1) - trend.min()) / range);
            float norm1 = (float) ((values.get(i) - trend.min()) / range);
            float y0 = box.y() + box.height() - norm0 * box.height();
            float y1 = box.y() + box.height() - norm1 * box.height();

            // Color: green for low values, yellow for mid, red for high
            float t = (norm0 + norm1) * 0.5f;
            float r = Math.min(1f, t * 2f);
            float g = Math.min(1f, (1f - t) * 2f);

            textRenderer.drawLine(x0, y0, x1, y1, r, g, 0.2f, screenW, screenH);
        }
    }

    @Override
    public void drawText(String text, float x, float y, int argbColor) {
        float a = ((argbColor >> 24) & 0xFF) / 255f;
        float r = ((argbColor >> 16) & 0xFF) / 255f;
        float g = ((argbColor >> 8) & 0xFF) / 255f;
        float b = (argbColor & 0xFF) / 255f;
        textRenderer.drawText(text, x, y, TEXT_SCALE, r * a, g * a, b * a, screenW, screenH);
    }

    @Override
    public void renderFocus(DebugOverlayPanel panel, LayoutBox screen,
                             List<DebugViewSnapshot.DebugTimelineEvent> timelineEvents) {
        this.screenW = (int) screen.width();
        this.screenH = (int) screen.height();

        // Disable depth test so 2D overlay renders on top of 3D scene
        org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);

        beginOverlay();

        float margin = 12f;
        float x = margin;
        float y = margin;
        float w = screen.width() - margin * 2;

        // Full-screen background
        float[] bg = switch (panel.severity()) {
            case WARNING -> BG_WARNING;
            case ERROR -> BG_ERROR;
            default -> BG_NORMAL;
        };
        textRenderer.drawRect(0, 0, screen.width(), screen.height(),
            bg[0], bg[1], bg[2], bg[3], screenW, screenH);

        // Header: title + category + severity
        float[] titleColor = panel.highlighted() ? TEXT_WARNING : TEXT_HEADER;
        textRenderer.drawText("[ FOCUS ] " + panel.title(), x, y, 2.5f,
            titleColor[0], titleColor[1], titleColor[2], screenW, screenH);
        y += 22f;

        String meta = String.format("id: %s/%s  severity: %s  region: %s",
            panel.id().category(), panel.id().stableKey(),
            panel.severity(), panel.region());
        textRenderer.drawText(meta, x, y, TEXT_SCALE * 0.85f,
            TEXT_DIM[0], TEXT_DIM[1], TEXT_DIM[2], screenW, screenH);
        y += ROW_HEIGHT + 4;

        // Separator
        textRenderer.drawRect(x, y, w, 1f, 0.3f, 0.3f, 0.3f, 1f, screenW, screenH);
        y += 6f;

        // Full metrics (no truncation)
        textRenderer.drawText("Metrics", x, y, HEADER_SCALE,
            TEXT_HEADER[0], TEXT_HEADER[1], TEXT_HEADER[2], screenW, screenH);
        y += ROW_HEIGHT + 2;

        for (var row : panel.rows()) {
            float[] color = switch (row.severity()) {
                case WARNING -> TEXT_WARNING;
                case ERROR -> TEXT_ERROR;
                default -> TEXT_NORMAL;
            };
            // Full precision, no truncation
            textRenderer.drawText("  " + row.label() + ": " + row.value(), x, y, TEXT_SCALE,
                color[0], color[1], color[2], screenW, screenH);
            y += ROW_HEIGHT;
        }

        // Flags
        if (!panel.flags().isEmpty()) {
            y += 4f;
            textRenderer.drawText("Flags", x, y, HEADER_SCALE,
                TEXT_HEADER[0], TEXT_HEADER[1], TEXT_HEADER[2], screenW, screenH);
            y += ROW_HEIGHT + 2;

            for (var flag : panel.flags()) {
                float[] fc = switch (flag.state()) {
                    case ACTIVE -> FLAG_ACTIVE;
                    case WARNING -> FLAG_WARN;
                    case ERROR -> FLAG_ERR;
                    default -> FLAG_OK;
                };
                textRenderer.drawText("  [" + flag.state() + "] " + flag.name(), x, y, TEXT_SCALE,
                    fc[0], fc[1], fc[2], screenW, screenH);
                y += ROW_HEIGHT;
            }
        }

        // Enlarged trends
        if (!panel.trends().isEmpty()) {
            y += 6f;
            textRenderer.drawText("Trends", x, y, HEADER_SCALE,
                TEXT_HEADER[0], TEXT_HEADER[1], TEXT_HEADER[2], screenW, screenH);
            y += ROW_HEIGHT + 2;

            float trendWidth = w * 0.8f;
            float trendHeight = 40f; // enlarged
            for (var trend : panel.trends()) {
                textRenderer.drawText("  " + trend.metricName() +
                    String.format("  [min=%.2f max=%.2f]", trend.min(), trend.max()),
                    x, y, TEXT_SCALE * 0.85f, TEXT_DIM[0], TEXT_DIM[1], TEXT_DIM[2], screenW, screenH);
                y += ROW_HEIGHT;
                var trendBox = new LayoutBox(x + 8, y, trendWidth, trendHeight);
                drawTrend(trend, trendBox);
                drawTrendAnnotations(trendBox, timelineEvents);
                y += trendHeight + 6;
            }
        }

        // Recent events for this category
        y += 6f;
        textRenderer.drawRect(x, y, w, 1f, 0.3f, 0.3f, 0.3f, 1f, screenW, screenH);
        y += 6f;
        textRenderer.drawText("Recent Events (" + timelineEvents.size() + ")", x, y, HEADER_SCALE,
            TEXT_HEADER[0], TEXT_HEADER[1], TEXT_HEADER[2], screenW, screenH);
        y += ROW_HEIGHT + 2;

        if (timelineEvents.isEmpty()) {
            textRenderer.drawText("  (no events for this category)", x, y, TEXT_SCALE,
                TEXT_DIM[0], TEXT_DIM[1], TEXT_DIM[2], screenW, screenH);
        } else {
            int shown = 0;
            for (int i = timelineEvents.size() - 1; i >= 0 && shown < 15; i--) {
                var event = timelineEvents.get(i);
                float[] ec = "ERROR".equals(event.severity()) || "CRITICAL".equals(event.severity())
                    ? TEXT_ERROR : TEXT_WARNING;
                String line = String.format("  T%d [%s] %s: %s",
                    event.frameNumber(), event.severity(), event.source(), event.message());
                textRenderer.drawText(line, x, y, TEXT_SCALE * 0.85f,
                    ec[0], ec[1], ec[2], screenW, screenH);
                y += ROW_HEIGHT;
                shown++;
            }
        }

        // Navigation hint
        textRenderer.drawText("F=exit focus  [/]=cycle panels", x,
            screen.height() - 20, TEXT_SCALE * 0.85f,
            TEXT_DIM[0], TEXT_DIM[1], TEXT_DIM[2], screenW, screenH);

        endOverlay();
    }

    @Override
    public void endOverlay() {
        textRenderer.endFrame();
    }

    /** Draw event markers on a trend graph in focus mode. */
    private void drawTrendAnnotations(LayoutBox box,
                                       java.util.List<DebugViewSnapshot.DebugTimelineEvent> events) {
        if (events.isEmpty()) return;

        long minFrame = Long.MAX_VALUE, maxFrame = Long.MIN_VALUE;
        for (var e : events) {
            if (e.frameNumber() < minFrame) minFrame = e.frameNumber();
            if (e.frameNumber() > maxFrame) maxFrame = e.frameNumber();
        }
        long frameRange = Math.max(1, maxFrame - minFrame);

        for (var event : events) {
            float t = (float)(event.frameNumber() - minFrame) / frameRange;
            float ex = box.x() + t * box.width();

            boolean severe = "ERROR".equals(event.severity()) || "CRITICAL".equals(event.severity());
            float r = severe ? 1f : 1f;
            float g = severe ? 0.2f : 0.8f;
            float b = severe ? 0.2f : 0.2f;
            float a = severe ? 0.7f : 0.5f;
            float tickH = severe ? box.height() : box.height() * 0.6f;

            textRenderer.drawRect(ex, box.y(), 1.5f, tickH, r, g, b, a, screenW, screenH);
        }
    }

    private boolean isEmpty(DebugOverlayPanel panel) {
        return panel.rows().size() <= 1
            && panel.flags().isEmpty()
            && panel.trends().isEmpty()
            && panel.rows().stream().anyMatch(r -> "no data".equals(r.value()));
    }

    private float panelHeight(DebugOverlayPanel panel) {
        // Compact empty panels: title + one "no data" row, minimal padding
        if (isEmpty(panel)) {
            return COMPACT_HEIGHT;
        }

        int lines = 1; // title
        lines += panel.rows().size();
        lines += panel.flags().isEmpty() ? 0 : panel.flags().size();
        float height = PANEL_PADDING * 2 + lines * ROW_HEIGHT + 4;

        // Trends: label + sparkline per trend, with breathing room
        if (!panel.trends().isEmpty()) {
            height += 6; // gap before trends
            height += panel.trends().size() * (ROW_HEIGHT * 0.7f + TREND_HEIGHT + 4);
        }

        // Cap alert panel height
        if (panel.region() == PanelRegion.TOP && height > MAX_ALERT_HEIGHT) {
            height = MAX_ALERT_HEIGHT;
        }

        return height;
    }
}
