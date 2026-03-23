package org.dynamisengine.light.impl.vulkan.ui;

import org.dynamisengine.ui.debug.builder.DebugViewSnapshot;
import org.dynamisengine.ui.debug.model.*;
import org.dynamisengine.ui.debug.render.DebugOverlayRenderer;

import java.util.List;

/**
 * Vulkan implementation of {@link DebugOverlayRenderer} SPI.
 *
 * <p>Thin adapter that translates overlay draw calls into
 * {@link VulkanUiRenderer} primitives. Contains no logic — only delegation.
 *
 * <p>This is the canonical backend; OpenGL is the fallback.
 */
public final class VulkanDebugOverlayRenderer implements DebugOverlayRenderer {

    // Layout constants (mirroring OpenGL renderer for parity)
    private static final float PANEL_WIDTH = 280f;
    private static final float PANEL_PADDING = 8f;
    private static final float ROW_HEIGHT = 14f;
    private static final float PANEL_GAP = 6f;
    private static final float COLUMN_GAP = 10f;
    private static final float TEXT_SCALE = 1.0f;
    private static final float HEADER_SCALE = 1.2f;
    private static final float TREND_HEIGHT = 24f;
    private static final float COMPACT_HEIGHT = 30f;
    private static final float MAX_ALERT_HEIGHT = 80f;

    // Colors (packed ABGR via UiQuadVertex.packColor)
    private static final int COLOR_BG_NORMAL = UiQuadVertex.packColor(0f, 0f, 0f, 0.75f);
    private static final int COLOR_BG_WARNING = UiQuadVertex.packColor(0.15f, 0.12f, 0f, 0.8f);
    private static final int COLOR_BG_ERROR = UiQuadVertex.packColor(0.2f, 0.05f, 0.05f, 0.85f);
    private static final int COLOR_BG_DIM = UiQuadVertex.packColor(0f, 0f, 0f, 0.4f);
    private static final int COLOR_TEXT_NORMAL = UiQuadVertex.packColor(0.7f, 0.8f, 0.7f, 1f);
    private static final int COLOR_TEXT_WARNING = UiQuadVertex.packColor(1f, 0.8f, 0.2f, 1f);
    private static final int COLOR_TEXT_ERROR = UiQuadVertex.packColor(1f, 0.3f, 0.3f, 1f);
    private static final int COLOR_TEXT_HEADER = UiQuadVertex.packColor(0.3f, 1f, 0.6f, 1f);
    private static final int COLOR_TEXT_DIM = UiQuadVertex.packColor(0.5f, 0.55f, 0.5f, 1f);
    private static final int COLOR_FLAG_OK = UiQuadVertex.packColor(0.4f, 0.5f, 0.4f, 1f);
    private static final int COLOR_FLAG_ACTIVE = UiQuadVertex.packColor(0.3f, 0.9f, 0.3f, 1f);
    private static final int COLOR_FLAG_WARN = UiQuadVertex.packColor(0.9f, 0.7f, 0.2f, 1f);
    private static final int COLOR_FLAG_ERR = UiQuadVertex.packColor(0.9f, 0.2f, 0.2f, 1f);
    private static final int COLOR_HIGHLIGHT = UiQuadVertex.packColor(1f, 0.8f, 0.2f, 1f);

    private final VulkanUiRenderer renderer;

    public VulkanDebugOverlayRenderer(VulkanUiRenderer renderer) {
        this.renderer = renderer;
    }

    /**
     * Render all panels with two-column layout.
     */
    public void renderPanels(List<DebugOverlayPanel> panels, int screenW, int screenH) {
        float startX = 8f;
        float y = 8f;

        // TOP panels (full width)
        for (var panel : panels) {
            if (panel.region() != PanelRegion.TOP) continue;
            float h = panelHeight(panel);
            drawPanel(panel, new LayoutBox(startX, y, PANEL_WIDTH * 2 + COLUMN_GAP, h));
            y += h + PANEL_GAP;
        }

        // GRID panels (two columns)
        float col1X = startX, col2X = startX + PANEL_WIDTH + COLUMN_GAP;
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
    }

    @Override
    public void beginOverlay() {
        // No-op: VulkanUiRenderer.beginFrame() is called by the frame hook
    }

    @Override
    public void drawPanel(DebugOverlayPanel panel, LayoutBox box) {
        boolean compact = isEmpty(panel);

        // Background (un-clipped — drawn at full panel size)
        int bg = compact ? COLOR_BG_DIM : switch (panel.severity()) {
            case WARNING -> COLOR_BG_WARNING;
            case ERROR -> COLOR_BG_ERROR;
            default -> COLOR_BG_NORMAL;
        };
        renderer.drawQuad(box.x(), box.y(), box.width(), box.height(), bg);

        if (panel.highlighted()) {
            renderer.drawQuad(box.x(), box.y(), 3f, box.height(), COLOR_HIGHLIGHT);
        }

        // Clip all content to panel bounds
        renderer.pushScissor((int) box.x(), (int) box.y(), (int) box.width(), (int) box.height());

        float x = box.x() + PANEL_PADDING;
        float y = box.y() + (compact ? 4f : PANEL_PADDING);

        if (compact) {
            renderer.drawText(panel.title() + " - no data", x, y, TEXT_SCALE, COLOR_TEXT_DIM);
            renderer.popScissor();
            return;
        }

        // Title
        int titleColor = panel.highlighted() ? COLOR_TEXT_WARNING : COLOR_TEXT_HEADER;
        renderer.drawText(panel.title(), x, y, HEADER_SCALE, titleColor);
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

        // Trends
        if (!panel.trends().isEmpty()) {
            y += 4;
            float trendWidth = box.width() - PANEL_PADDING * 2;
            for (var trend : panel.trends()) {
                renderer.drawText(trend.metricName(), x, y, TEXT_SCALE * 0.75f, COLOR_TEXT_DIM);
                y += ROW_HEIGHT * 0.7f;
                drawTrend(trend, new LayoutBox(x, y, trendWidth, TREND_HEIGHT));
                y += TREND_HEIGHT + 2;
            }
        }

        renderer.popScissor();
    }

    @Override
    public void drawRow(DebugOverlayRow row, float x, float y, float width) {
        int color = switch (row.severity()) {
            case WARNING -> COLOR_TEXT_WARNING;
            case ERROR -> COLOR_TEXT_ERROR;
            default -> COLOR_TEXT_NORMAL;
        };
        String line = row.label() + ": " + row.value();
        renderer.drawText(truncate(line, width), x, y, TEXT_SCALE, color);
    }

    @Override
    public void drawFlag(DebugFlagView flag, float x, float y) {
        int color = switch (flag.state()) {
            case ACTIVE -> COLOR_FLAG_ACTIVE;
            case WARNING -> COLOR_FLAG_WARN;
            case ERROR -> COLOR_FLAG_ERR;
            default -> COLOR_FLAG_OK;
        };
        renderer.drawText("[" + flag.state().name() + "] " + flag.name(), x, y, TEXT_SCALE * 0.9f, color);
    }

    @Override
    public void drawTrend(DebugMiniTrend trend, LayoutBox box) {
        var values = trend.values();
        if (values.size() < 2) return;

        // Background
        renderer.drawQuad(box.x(), box.y(), box.width(), box.height(),
            UiQuadVertex.packColor(0.05f, 0.05f, 0.08f, 0.9f));

        double range = trend.max() - trend.min();
        if (range < 0.0001) range = 1.0;

        float stepX = box.width() / (values.size() - 1);
        for (int i = 1; i < values.size(); i++) {
            float x0 = box.x() + (i - 1) * stepX;
            float x1 = box.x() + i * stepX;
            float norm0 = (float)((values.get(i - 1) - trend.min()) / range);
            float norm1 = (float)((values.get(i) - trend.min()) / range);
            float y0 = box.y() + box.height() - norm0 * box.height();
            float y1 = box.y() + box.height() - norm1 * box.height();

            float t = (norm0 + norm1) * 0.5f;
            float r = Math.min(1f, t * 2f);
            float g = Math.min(1f, (1f - t) * 2f);
            int lineColor = UiQuadVertex.packColor(r, g, 0.2f, 1f);
            renderer.drawLine(x0, y0, x1, y1, lineColor);
        }
    }

    @Override
    public void drawText(String text, float x, float y, int argbColor) {
        // Convert ARGB to our pack format
        float a = ((argbColor >> 24) & 0xFF) / 255f;
        float r = ((argbColor >> 16) & 0xFF) / 255f;
        float g = ((argbColor >> 8) & 0xFF) / 255f;
        float b = (argbColor & 0xFF) / 255f;
        renderer.drawText(text, x, y, TEXT_SCALE, UiQuadVertex.packColor(r, g, b, a));
    }

    @Override
    public void renderFocus(DebugOverlayPanel panel, LayoutBox screen,
                             List<DebugViewSnapshot.DebugTimelineEvent> timelineEvents) {
        float margin = 12f;
        float x = margin;
        float y = margin;
        float w = screen.width() - margin * 2;

        // Full-screen background (un-clipped)
        int bg = switch (panel.severity()) {
            case WARNING -> COLOR_BG_WARNING;
            case ERROR -> COLOR_BG_ERROR;
            default -> COLOR_BG_NORMAL;
        };
        renderer.drawQuad(0, 0, screen.width(), screen.height(), bg);

        // Clip content to focus area
        renderer.pushScissor((int) margin, (int) margin,
            (int)(screen.width() - margin * 2), (int)(screen.height() - margin * 2));

        // Header
        int titleColor = panel.highlighted() ? COLOR_TEXT_WARNING : COLOR_TEXT_HEADER;
        renderer.drawText("[ FOCUS ] " + panel.title(), x, y, HEADER_SCALE * 1.3f, titleColor);
        y += 22f;

        renderer.drawText(String.format("id: %s/%s  severity: %s  region: %s",
            panel.id().category(), panel.id().stableKey(), panel.severity(), panel.region()),
            x, y, TEXT_SCALE * 0.85f, COLOR_TEXT_DIM);
        y += ROW_HEIGHT + 4;

        // Separator
        renderer.drawQuad(x, y, w, 1f, UiQuadVertex.packColor(0.3f, 0.3f, 0.3f, 1f));
        y += 6f;

        // Full metrics
        renderer.drawText("Metrics", x, y, HEADER_SCALE, COLOR_TEXT_HEADER);
        y += ROW_HEIGHT + 2;
        for (var row : panel.rows()) {
            int color = switch (row.severity()) {
                case WARNING -> COLOR_TEXT_WARNING;
                case ERROR -> COLOR_TEXT_ERROR;
                default -> COLOR_TEXT_NORMAL;
            };
            renderer.drawText("  " + row.label() + ": " + row.value(), x, y, TEXT_SCALE, color);
            y += ROW_HEIGHT;
        }

        // Flags
        if (!panel.flags().isEmpty()) {
            y += 4f;
            renderer.drawText("Flags", x, y, HEADER_SCALE, COLOR_TEXT_HEADER);
            y += ROW_HEIGHT + 2;
            for (var flag : panel.flags()) {
                int fc = switch (flag.state()) {
                    case ACTIVE -> COLOR_FLAG_ACTIVE;
                    case WARNING -> COLOR_FLAG_WARN;
                    case ERROR -> COLOR_FLAG_ERR;
                    default -> COLOR_FLAG_OK;
                };
                renderer.drawText("  [" + flag.state() + "] " + flag.name(), x, y, TEXT_SCALE, fc);
                y += ROW_HEIGHT;
            }
        }

        // Enlarged trends
        if (!panel.trends().isEmpty()) {
            y += 6f;
            renderer.drawText("Trends", x, y, HEADER_SCALE, COLOR_TEXT_HEADER);
            y += ROW_HEIGHT + 2;
            float trendWidth = w * 0.8f;
            for (var trend : panel.trends()) {
                renderer.drawText("  " + trend.metricName() +
                    String.format("  [min=%.2f max=%.2f]", trend.min(), trend.max()),
                    x, y, TEXT_SCALE * 0.85f, COLOR_TEXT_DIM);
                y += ROW_HEIGHT;
                drawTrend(trend, new LayoutBox(x + 8, y, trendWidth, 40f));
                y += 46f;
            }
        }

        // Recent events
        y += 6f;
        renderer.drawQuad(x, y, w, 1f, UiQuadVertex.packColor(0.3f, 0.3f, 0.3f, 1f));
        y += 6f;
        renderer.drawText("Recent Events (" + timelineEvents.size() + ")", x, y, HEADER_SCALE, COLOR_TEXT_HEADER);
        y += ROW_HEIGHT + 2;
        if (timelineEvents.isEmpty()) {
            renderer.drawText("  (no events)", x, y, TEXT_SCALE, COLOR_TEXT_DIM);
        } else {
            int shown = 0;
            for (int i = timelineEvents.size() - 1; i >= 0 && shown < 15; i--) {
                var event = timelineEvents.get(i);
                int ec = "ERROR".equals(event.severity()) || "CRITICAL".equals(event.severity())
                    ? COLOR_TEXT_ERROR : COLOR_TEXT_WARNING;
                renderer.drawText(String.format("  T%d [%s] %s: %s",
                    event.frameNumber(), event.severity(), event.source(), event.message()),
                    x, y, TEXT_SCALE * 0.85f, ec);
                y += ROW_HEIGHT;
                shown++;
            }
        }

        renderer.popScissor();

        // Navigation hint (un-clipped, at screen bottom)
        renderer.drawText("F=exit focus  [/]=cycle panels", x,
            screen.height() - 20, TEXT_SCALE * 0.85f, COLOR_TEXT_DIM);
    }

    @Override
    public void endOverlay() {
        // No-op: VulkanUiRenderer.endFrame() is called by the frame hook
    }

    // --- Helpers ---

    private boolean isEmpty(DebugOverlayPanel panel) {
        return panel.rows().size() <= 1 && panel.flags().isEmpty() && panel.trends().isEmpty()
            && panel.rows().stream().anyMatch(r -> "no data".equals(r.value()));
    }

    private float panelHeight(DebugOverlayPanel panel) {
        if (isEmpty(panel)) return COMPACT_HEIGHT;
        int lines = 1 + panel.rows().size();
        lines += panel.flags().isEmpty() ? 0 : panel.flags().size();
        float height = PANEL_PADDING * 2 + lines * ROW_HEIGHT + 4;
        if (!panel.trends().isEmpty()) {
            height += 6 + panel.trends().size() * (ROW_HEIGHT * 0.7f + TREND_HEIGHT + 4);
        }
        if (panel.region() == PanelRegion.TOP && height > MAX_ALERT_HEIGHT) height = MAX_ALERT_HEIGHT;
        return height;
    }

    private static String truncate(String text, float widthPx) {
        int maxChars = Math.max(5, (int)(widthPx / (8 * TEXT_SCALE)));
        if (text.length() <= maxChars) return text;
        return text.substring(0, maxChars - 2) + "..";
    }
}
