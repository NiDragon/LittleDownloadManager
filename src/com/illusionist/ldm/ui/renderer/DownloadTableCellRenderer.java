package com.illusionist.ldm.ui.renderer;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

public class DownloadTableCellRenderer extends JProgressBar implements TableCellRenderer {
    private String state;
    private int percent;

    @Override
    public Component getTableCellRendererComponent (JTable table,
                                                    Object value,
                                                    boolean isSelected,
                                                    boolean isFocus,
                                                    int row,
                                                    int column)
    {
        String[] pair = ((String)value).split(":");

        state = pair[0];
        percent = Integer.parseInt(pair[1]);

        if(percent == -1)
            setValue(100);
        else if(percent != -2)
            setValue(percent);

        return this;
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);

        String text;

        if(percent == -1) {
            text = String.format("%s", state);
        } else {
            text = String.format("%s %d%%", state, getValue());
        }

        var rect = g.getClipBounds();
        var font = getFont();

        FontMetrics metrics = g.getFontMetrics(font);
        int x = rect.x + (rect.width - metrics.stringWidth(text)) / 2;
        int y = rect.y + ((rect.height - metrics.getHeight()) / 2) + metrics.getAscent();
        g.setColor(Color.black);
        g.setFont(font);

        g.drawString(text, x, y);
    }
}
