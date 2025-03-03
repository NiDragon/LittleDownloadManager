package com.illusionist.ldm.ui;

import com.illusionist.ldm.network.DataReceiveListener;
import com.illusionist.ldm.network.FileDownloader;
import com.illusionist.ldm.ui.dialog.DlgDownload;
import com.illusionist.ldm.ui.renderer.DownloadTableCellRenderer;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.*;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static com.illusionist.ldm.util.StringFormatUtil.bytesToString;
import static com.illusionist.ldm.util.StringFormatUtil.secondsToTime;

public final class MainWindow extends JFrame {

    public final ArrayList<FileDownloader> downloadList = new ArrayList<>();

    private JPanel contentPane;
    private JTable downloadTableView;

    private final int ID_COLUMN = 0;
    private final int STATUS_COLUMN = 2;
    private final int DOWNSPEED_COLUMN = 3;
    private final int ETA_COLUMN = 4;

    private int availableId = 1;
    DefaultTableModel downloadTableData;

    public MainWindow() {
        super("Little Download Manager");

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(800, 600));

        JMenuBar menuBar = new JMenuBar();

        JMenu fileMenu = new JMenu("File");
        JMenuItem addItem = new JMenuItem("Add Download");
        addItem.addActionListener(this::addDownload);

        JMenuItem exitItem = new JMenuItem("Exit");
        //noinspection CodeBlock2Expr
        exitItem.addActionListener((ActionEvent e) -> {
            dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
        });

        fileMenu.add(addItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);
        menuBar.add(fileMenu);

        setJMenuBar(menuBar);

        setContentPane(contentPane);

        String[] COLUMN_NAMES = {"#", "Filename", "Status", "Down Speed", "ETA"};
        downloadTableData = new DefaultTableModel(null, COLUMN_NAMES);

        downloadTableView.setDefaultEditor(Object.class, null);
        downloadTableView.setModel(downloadTableData);
        downloadTableView.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Set custom cell renderer
        downloadTableView.getColumnModel().getColumn(2).setCellRenderer(new DownloadTableCellRenderer());

        // Center cells 0, 3 and 4
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment( JLabel.CENTER );

        downloadTableView.getColumnModel().getColumn(ID_COLUMN).setCellRenderer( centerRenderer );
        downloadTableView.getColumnModel().getColumn(DOWNSPEED_COLUMN).setCellRenderer( centerRenderer );
        downloadTableView.getColumnModel().getColumn(ETA_COLUMN).setCellRenderer( centerRenderer );

        JPopupMenu downloadPopup = getPopupMenu(downloadTableView);

        downloadTableView.setComponentPopupMenu(downloadPopup);

        addWindowListener(new MainWindowListener());
    }

    private void stopDownloading() {
        for(FileDownloader download : downloadList) {
            int status = download.getDownloadStatus();

            if(status == FileDownloader.RUNNING || status == FileDownloader.PAUSED)
            {
                download.stop();
            }
        }
    }

    private int getNumActiveDownloads() {
        int numActiveDownloads = 0;

        for(FileDownloader download : downloadList) {
            int status = download.getDownloadStatus();

            if(status == FileDownloader.RUNNING || status == FileDownloader.PAUSED)
                numActiveDownloads++;
        }

        return numActiveDownloads;
    }

    private JPopupMenu getPopupMenu(JTable downloadTable) {
        JPopupMenu downloadPopup = new JPopupMenu();

        // Create Items
        JMenuItem pauseItem = new JMenuItem("Pause");

        JMenuItem stopItem = new JMenuItem("Stop");

        JMenuItem showItem = new JMenuItem("Show In Folder");

        // Add Events
        pauseItem.addActionListener((ActionEvent e) -> {
            FileDownloader dl = getDownloaderFromIndex(downloadTable.getSelectedRow());

            if(dl != null) {
                if(dl.getDownloadStatus() == FileDownloader.RUNNING)
                {
                    dl.pause();
                }
                else if(dl.getDownloadStatus() == FileDownloader.PAUSED)
                {
                    dl.start();
                }
                else {
                    // Try and restart the download
                    dl.start();
                }
            }
        });

        showItem.addActionListener((ActionEvent e) -> {
            FileDownloader dl = getDownloaderFromIndex(downloadTable.getSelectedRow());

            if(dl != null) {
                File file = new File (dl.getFilePath()).getParentFile();
                Desktop desktop = Desktop.getDesktop();
                try {
                    desktop.open(file);
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });

        stopItem.addActionListener((ActionEvent e) -> {
            int rowIndex = downloadTable.getSelectedRow();
            FileDownloader dl = getDownloaderFromIndex(rowIndex);

            if(dl != null) {
                int status = dl.getDownloadStatus();
                int downloadId = (int)dl.getUserData();

                if(status == FileDownloader.RUNNING || status == FileDownloader.PAUSED) {
                    dl.stop();
                } else {
                    int removeIndex = -1;

                    for(int i = 0; i < downloadList.size(); i++) {
                        int currentId = (int)downloadList.get(i).getUserData();

                        if(currentId == downloadId) {
                            removeIndex = i;
                            break;
                        }
                    }

                    downloadTableData.removeRow(rowIndex);

                    if(removeIndex != -1)
                        downloadList.remove(removeIndex);
                }
            }
        });

        // Add Items
        downloadPopup.add(pauseItem);
        downloadPopup.addSeparator();
        downloadPopup.add(showItem);
        downloadPopup.addSeparator();
        downloadPopup.add(stopItem);

        // Create Listener
        downloadPopup.addPopupMenuListener(new DownloadMenuListener(downloadTable, downloadPopup, stopItem, pauseItem));

        return downloadPopup;
    }

    private boolean verifyDownload(String filepath, String hash, int rowIndex) {
        boolean result = false;

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");

            File file = new File(filepath);

            // If this path does not exist or is not a file fail
            if(!file.exists() || !file.isFile())
                return false;

            try (FileInputStream inputStream = new FileInputStream(file)) {

                int bytesRead = 0;
                int totalBytesRead = 0;
                long fileSizeInBytes = file.length();
                byte[] buffer = new byte[16384];

                while(bytesRead != -1)
                {
                    bytesRead = inputStream.read(buffer);

                    if(bytesRead > 0) {
                        md.update(buffer, 0, bytesRead);
                        totalBytesRead += bytesRead;
                    }

                    if(rowIndex != -1)
                    {
                        long percent = (long)(((double)totalBytesRead / (double)fileSizeInBytes) * 100);
                        updateCell(rowIndex, STATUS_COLUMN, String.format("Verifying:%d", percent));
                    }
                }

                byte[] hashBytes = md.digest();
                String computedHash = HexFormat.of().formatHex(hashBytes);

                if(hash.equalsIgnoreCase(computedHash))
                    result = true;
            }

        } catch (NoSuchAlgorithmException | IOException e) {
            throw new RuntimeException(e);
        }

        return result;
    }

    @SuppressWarnings("unused")
    private boolean verifyDownload(String filepath, String hash) {
        return verifyDownload(filepath, hash, -1);
    }

    public void addDownload(ActionEvent e) {
        DlgDownload dialog = new DlgDownload();
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);

        if (dialog.getResult()) {
            FileDownloader downloader = new FileDownloader();

            downloader.setDownloadUrl(dialog.getUrl());
            downloader.setFilePath(Paths.get(dialog.getDirectory(), dialog.getFilename()).toString());

            int downloadId = availableId++;

            downloader.setUserData(downloadId);

            Object[] downloadData = {downloadId, dialog.getFilename(), dialog.getStartPaused() ? "Paused:0" : "Starting:0", "0kB", "∞"};

            downloadTableData.addRow(downloadData);

            downloader.setOnDownloadRunning((ActionEvent ex) -> {
                int rowIndex = getRowIndex(downloadId);
                updateCell(rowIndex, STATUS_COLUMN, "Downloading:0");
            });

            downloader.setOnDownloadCompleted((ActionEvent ex) -> {
                int rowIndex = getRowIndex(downloadId);

                updateCell(rowIndex, DOWNSPEED_COLUMN, "");
                updateCell(rowIndex, ETA_COLUMN, "");

                if(dialog.getVerify()) {

                    updateCell(rowIndex, STATUS_COLUMN, "Verifying:-1");

                    if(verifyDownload(downloader.getFilePath(), dialog.getSHA1(), rowIndex)) {
                        updateCell(rowIndex, STATUS_COLUMN, "Complete:-1");
                    } else {
                        updateCell(rowIndex, STATUS_COLUMN, "Checksum Failed!:-1");
                    }
                } else {
                    updateCell(rowIndex, STATUS_COLUMN, "Complete:-1");
                }
            });

            downloader.setOnDownloadPaused((ActionEvent ex) -> {
                int rowIndex = getRowIndex(downloadId);

                updateCell(rowIndex, ETA_COLUMN, "∞");
                updateCell(rowIndex, DOWNSPEED_COLUMN, "0 B/s");
                updateCell(rowIndex, STATUS_COLUMN, "Paused:-2");
            });

            downloader.setOnDownloadStopped((ActionEvent ex) -> {
                int rowIndex = getRowIndex(downloadId);

                updateCell(rowIndex, ETA_COLUMN, "");
                updateCell(rowIndex, DOWNSPEED_COLUMN, "");
                updateCell(rowIndex, STATUS_COLUMN, "Stopped:-1");
            });

            // 1st start, 2nd end
            final long[] bytesPerSecond = {0, 0};
            final Instant[] lastTime = {Instant.now()};

            //noinspection Convert2Lambda
            downloader.setOnDataRecv(new DataReceiveListener() {
                @Override
                public void onDataReceive(FileDownloader source, long bytesRecv, long bytesTotal) {
                    int rowIndex = getRowIndex(downloadId);

                    int percent = (int)(((double)bytesRecv / (double)bytesTotal) * 100);

                    // Calculate a time here...
                    if(Duration.between(lastTime[0], Instant.now()).getSeconds() > 0) {
                        lastTime[0] = Instant.now();

                        bytesPerSecond[0] = bytesRecv - bytesPerSecond[1];

                        bytesPerSecond[1] = bytesRecv;

                        // now find what remains
                        long bytesLeft = bytesTotal - bytesRecv;

                        // use our bytesPerSecond[0] to determine how long this operation would take in sections
                        if(bytesPerSecond[0] != 0) {
                            long seconds = bytesLeft / bytesPerSecond[0];

                            updateCell(rowIndex, DOWNSPEED_COLUMN, bytesToString(bytesPerSecond[0]));
                            updateCell(rowIndex, ETA_COLUMN, secondsToTime(seconds));
                        } else {
                            updateCell(rowIndex, DOWNSPEED_COLUMN, bytesToString(0));
                            updateCell(rowIndex, ETA_COLUMN, "∞");
                        }
                    }

                    updateCell(rowIndex, STATUS_COLUMN, String.format("Downloading:%d", percent));
                }
            });

            if (!dialog.getStartPaused()) {
                downloader.start();
            }

            downloadList.add(downloader);
        }
    }

    public int getRowIndex(int id) {
        int rowCount = downloadTableData.getRowCount();

        for (int i = 0; i < rowCount; i++) {
            if (Objects.equals(downloadTableView.getValueAt(i, 0), id)) {
                return i;
            }
        }

        return -1;
    }

    public FileDownloader getDownloaderFromIndex(int rowIndex) {
        int downloadId = (int) downloadTableData.getValueAt(rowIndex, ID_COLUMN);

        for (FileDownloader fileDownloader : downloadList) {
            int id = (int)fileDownloader.getUserData();

            if (id == downloadId)
                return fileDownloader;
        }

        return null;
    }

    public void updateCell(int row, int column, Object value) {
        downloadTableData.setValueAt(value, row, column);
    }

    private class MainWindowListener implements WindowListener {
        @Override
        public void windowOpened(WindowEvent e) {}

        @Override
        public void windowClosing(WindowEvent e) {
            if(getNumActiveDownloads() > 0) {
                int option = JOptionPane.showConfirmDialog(
                        contentPane, "There are incomplete downloads are you sure you want to exit?", "Confirm Exit", JOptionPane.YES_NO_OPTION);

                if(option == JOptionPane.YES_OPTION){
                    stopDownloading();
                    setDefaultCloseOperation(EXIT_ON_CLOSE);//yes

                } else {
                    setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);//no
                }
            }
            else {
                setDefaultCloseOperation(EXIT_ON_CLOSE);
            }
        }

        @Override
        public void windowClosed(WindowEvent e) {}

        @Override
        public void windowIconified(WindowEvent e) {}

        @Override
        public void windowDeiconified(WindowEvent e) {}

        @Override
        public void windowActivated(WindowEvent e) {}

        @Override
        public void windowDeactivated(WindowEvent e) {}
    }

    private class DownloadMenuListener implements PopupMenuListener {
        private final JTable downloadTable;
        private final JPopupMenu downloadPopup;
        private final JMenuItem stopItem;
        private final JMenuItem pauseItem;

        public DownloadMenuListener(JTable downloadTable, JPopupMenu downloadPopup, JMenuItem stopItem, JMenuItem pauseItem) {
            this.downloadTable = downloadTable;
            this.downloadPopup = downloadPopup;
            this.stopItem = stopItem;
            this.pauseItem = pauseItem;
        }

        @Override
        public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
            //noinspection Convert2Lambda
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    int rowAtPoint = downloadTable.rowAtPoint(SwingUtilities.convertPoint(downloadPopup, new Point(0, 0), downloadTable));
                    if (rowAtPoint > -1) {
                        FileDownloader dl = getDownloaderFromIndex(rowAtPoint);

                        if(dl != null) {
                            if (dl.getDownloadStatus() == FileDownloader.RUNNING) {
                                pauseItem.setText("Pause");
                                stopItem.setText("Stop");
                            } else if (dl.getDownloadStatus() == FileDownloader.PAUSED) {
                                pauseItem.setText("Resume");
                                stopItem.setText("Stop");
                            } else {
                                pauseItem.setText("Restart");
                                stopItem.setText("Remove");
                            }
                        }

                        downloadTable.setRowSelectionInterval(rowAtPoint, rowAtPoint);
                    }
                }
            });
        }

        @Override
        public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
            // TODO Auto-generated method stub

        }

        @Override
        public void popupMenuCanceled(PopupMenuEvent e) {
            // TODO Auto-generated method stub

        }
    }
}
