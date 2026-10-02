import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import ui.MainFrame;

/** Điểm khởi chạy ứng dụng. Args: [ten] [portUnicast] — VD: java -cp bin Main Alice 5001 */
public class Main {
    public static void main(String[] args) {
        String name = args.length > 0 ? args[0] : "Alice";
        int port = 5001;
        if (args.length > 1) {
            try { port = Integer.parseInt(args[1]); } catch (NumberFormatException ignored) {}
        }
        final String fName = name;
        final int fPort = port;
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
            new MainFrame(fName, fPort).setVisible(true);
        });
    }
}
