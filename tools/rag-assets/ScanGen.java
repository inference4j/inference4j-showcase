/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;

/**
 * Renders the two "scanned" forms of the deposit-dispute demo: a printed form with typed values, tick boxes,
 * a signature line, a slight rotation, paper tint and grain. Values are typed only (no handwriting), because the
 * OCR pipeline reads printed text.
 *
 * <pre>
 * java -Djava.awt.headless=true ScanGen.java checklist pickup-checklist.png
 * java -Djava.awt.headless=true ScanGen.java damage damage-report.png
 * sips -s format jpeg -s formatOptions 80 pickup-checklist.png --out pickup-checklist.jpg
 * </pre>
 */
public class ScanGen {

    private static final int W = 1240;

    private static final Color INK = new Color(30, 30, 35);

    private static final Color TYPE = new Color(25, 35, 70);

    private static final Font LABEL = new Font("Helvetica", Font.BOLD, 26);

    private static final Font VALUE = new Font("Courier New", Font.PLAIN, 28);

    private static final Font SMALL = new Font("Helvetica", Font.PLAIN, 20);

    public static void main(String[] args) throws Exception {
        BufferedImage page = switch (args[0]) {
            case "checklist" -> checklist();
            case "damage" -> damageReport();
            case "receipt" -> receipt();
            default -> throw new IllegalArgumentException("checklist, damage or receipt");
        };
        double degrees = switch (args[0]) {
            case "checklist" -> 0.9;
            case "receipt" -> 0.5;
            default -> 0.7;
        };
        ImageIO.write(scan(page, degrees), "png", new File(args[1]));
    }

    private static BufferedImage checklist() {
        int h = 1560;
        BufferedImage page = blank(h);
        Graphics2D g = graphics(page);
        header(g, "Pickup checklist", "Form PC-3  rev. 2026");
        int y = 250;
        y = field(g, "Booking", "LB-4471", y);
        y = field(g, "Date", "31 October 2026, 10:05", y);
        y = field(g, "Customer name", "Alice Martin", y);
        y = field(g, "Phone", "+1 415 555 0187", y);
        y = field(g, "Email", "alice.martin@example.com", y);
        y = field(g, "ID number", "D4829-1736", y);
        y = field(g, "Bike", "E-bike E-14", y);
        y = field(g, "Return due", "2 November 2026, 19:00", y);
        y = field(g, "Deposit", "$150 taken on card", y);

        y += 20;
        g.setFont(LABEL);
        g.drawString("Condition at pickup", 80, y);
        g.setFont(SMALL);
        g.drawString("Notes", 360, y);
        g.drawString("OK", 880, y);
        g.drawString("Damage", 990, y);
        y += 14;
        g.drawLine(80, y, W - 80, y);
        y += 46;
        String[][] rows = {
                {"Frame and fork", "ok", ""},
                {"Brakes", "ok", ""},
                {"Gears", "ok", ""},
                {"Tyres", "ok", ""},
                {"Battery", "ok", "charged 100%"},
                {"Front light", "damage", "lens cracked, noted at pickup"},
                {"Rear light", "ok", ""},
                {"Lock and key", "ok", ""},
                {"Helmet issued", "ok", ""},
        };
        for (String[] row : rows) {
            g.setFont(LABEL);
            g.drawString(row[0], 80, y);
            box(g, 882, y - 22, row[1].equals("ok"));
            box(g, 1010, y - 22, row[1].equals("damage"));
            if (!row[2].isEmpty()) {
                g.setColor(TYPE);
                g.setFont(new Font("Courier New", Font.PLAIN, 24));
                g.drawString(row[2], 360, y);
                g.setColor(INK);
            }
            y += 56;
        }

        y += 40;
        g.setFont(SMALL);
        g.drawString("I have checked the bike with staff and agree with the condition recorded above.", 80, y);
        y += 90;
        signature(g, "Customer signature", "Alice Martin (signed)", 80, y);
        signature(g, "Staff", "Priya, front desk", 680, y);
        g.dispose();
        return page;
    }

    private static BufferedImage damageReport() {
        int h = 1500;
        BufferedImage page = blank(h);
        Graphics2D g = graphics(page);
        header(g, "Damage report", "Form DR-1  rev. 2026");
        int y = 250;
        y = field(g, "Report no.", "DR-2291", y);
        y = field(g, "Booking", "LB-4471", y);
        y = field(g, "Date", "2 November 2026", y);
        y = field(g, "Customer name", "Alice Martin", y);
        y = field(g, "Bike", "E-bike E-14", y);
        y = field(g, "Return due", "2 November 2026, 19:00", y);
        y = field(g, "Returned", "2 November 2026, 19:50", y);

        y += 20;
        g.setFont(LABEL);
        g.drawString("Charges taken from deposit", 80, y);
        g.setFont(SMALL);
        g.drawString("Amount", 960, y);
        y += 14;
        g.drawLine(80, y, W - 80, y);
        y += 50;
        String[][] charges = {
                {"Rear derailleur bent", "$60"},
                {"Front light lens cracked", "$25"},
                {"Late return, 1 started hour", "$8"},
        };
        for (String[] c : charges) {
            box(g, 80, y - 22, true);
            g.setColor(TYPE);
            g.setFont(VALUE);
            g.drawString(c[0], 130, y);
            g.drawString(c[1], 960, y);
            g.setColor(INK);
            y += 56;
        }
        g.drawLine(80, y - 30, W - 80, y - 30);
        y += 20;
        y = field(g, "Total taken from deposit", "$93", y);
        y = field(g, "Deposit", "$150", y);
        y = field(g, "Deposit refunded", "$57", y);

        y += 30;
        g.setFont(SMALL);
        g.drawString("Charges at the prices on the repair price list. Disputes within 30 days of return.", 80, y);
        y += 90;
        signature(g, "Checked by", "Daniel, workshop", 80, y);
        signature(g, "Date", "2 Nov 2026", 680, y);
        g.dispose();
        return page;
    }

    private static BufferedImage receipt() {
        int h = 1300;
        BufferedImage page = blank(h);
        Graphics2D g = graphics(page);
        header(g, "Rental receipt", "Form RR-2  rev. 2026");
        int y = 250;
        y = field(g, "Receipt no.", "R-10388", y);
        y = field(g, "Booking", "LB-4471", y);
        y = field(g, "Date", "31 October 2026", y);
        y = field(g, "Customer name", "Alice Martin", y);
        y = field(g, "Bike", "E-bike E-14", y);
        y = field(g, "Rental period", "31 Oct 10:05 to 2 Nov 19:00", y);

        y += 20;
        g.setFont(LABEL);
        g.drawString("Item", 80, y);
        g.setFont(SMALL);
        g.drawString("Amount", 960, y);
        y += 14;
        g.drawLine(80, y, W - 80, y);
        y += 50;
        String[][] items = {
                {"E-bike rental, 2 days at $35", "$70"},
                {"Theft protection, 2 days at $6", "$12"},
                {"Helmet and lock, included", "$0"},
        };
        for (String[] item : items) {
            g.setColor(TYPE);
            g.setFont(VALUE);
            g.drawString(item[0], 80, y);
            g.drawString(item[1], 960, y);
            g.setColor(INK);
            y += 56;
        }
        g.drawLine(80, y - 30, W - 80, y - 30);
        y += 20;
        y = field(g, "Total paid", "$82", y);
        y = field(g, "Deposit held", "$150, refundable", y);

        y += 30;
        g.setFont(SMALL);
        g.drawString("The deposit is held on the card and refunded after return, less any charges.", 80, y);
        y += 90;
        signature(g, "Issued by", "Priya, front desk", 80, y);
        g.dispose();
        return page;
    }

    private static BufferedImage blank(int h) {
        BufferedImage page = new BufferedImage(W, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = page.createGraphics();
        g.setColor(new Color(250, 248, 240));
        g.fillRect(0, 0, W, h);
        g.dispose();
        return page;
    }

    private static Graphics2D graphics(BufferedImage page) {
        Graphics2D g = page.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(INK);
        return g;
    }

    private static void header(Graphics2D g, String title, String formNo) {
        g.setFont(new Font("Helvetica", Font.BOLD, 40));
        g.drawString("LAKESIDE BIKE RENTALS", 80, 110);
        g.setFont(new Font("Helvetica", Font.PLAIN, 30));
        g.drawString(title, 80, 160);
        g.setFont(SMALL);
        g.drawString(formNo, W - 330, 160);
        g.setStroke(new BasicStroke(2f));
        g.drawLine(80, 185, W - 80, 185);
        g.setStroke(new BasicStroke(1.2f));
    }

    private static int field(Graphics2D g, String label, String value, int y) {
        g.setColor(INK);
        g.setFont(LABEL);
        g.drawString(label + ":", 80, y);
        g.setColor(TYPE);
        g.setFont(VALUE);
        g.drawString(value, 460, y);
        g.setColor(INK);
        return y + 60;
    }

    /** A printed tick box, ticked with two hand-drawn-looking strokes when {@code ticked}. */
    private static void box(Graphics2D g, int x, int y, boolean ticked) {
        g.setColor(INK);
        g.setStroke(new BasicStroke(1.5f));
        g.drawRect(x, y, 26, 26);
        if (ticked) {
            g.setColor(TYPE);
            g.setStroke(new BasicStroke(3f));
            g.drawLine(x + 4, y + 4, x + 22, y + 22);
            g.drawLine(x + 22, y + 4, x + 4, y + 22);
            g.setStroke(new BasicStroke(1.2f));
        }
        g.setColor(INK);
    }

    private static void signature(Graphics2D g, String label, String typed, int x, int y) {
        g.setColor(TYPE);
        g.setFont(new Font("Courier New", Font.ITALIC, 26));
        g.drawString(typed, x + 10, y - 12);
        g.setColor(INK);
        g.drawLine(x, y, x + 460, y);
        g.setFont(SMALL);
        g.drawString(label, x, y + 28);
    }

    /** Rotates slightly, as a sheet fed into a scanner at an angle, and adds paper grain. */
    private static BufferedImage scan(BufferedImage page, double degrees) {
        int w = page.getWidth(), h = page.getHeight();
        BufferedImage scan = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D s = scan.createGraphics();
        s.setColor(new Color(236, 234, 228));
        s.fillRect(0, 0, w, h);
        s.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        s.drawImage(page, AffineTransform.getRotateInstance(Math.toRadians(degrees), w / 2.0, h / 2.0), null);
        s.dispose();
        Random random = new Random(7);
        for (int py = 0; py < h; py++) {
            for (int px = 0; px < w; px++) {
                int rgb = scan.getRGB(px, py);
                int n = (int) (random.nextGaussian() * 6);
                int r = clamp(((rgb >> 16) & 0xff) + n);
                int gr = clamp(((rgb >> 8) & 0xff) + n);
                int b = clamp((rgb & 0xff) + n);
                scan.setRGB(px, py, (r << 16) | (gr << 8) | b);
            }
        }
        return scan;
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

}
