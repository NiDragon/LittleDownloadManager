package com.illusionist.ldm.ui.dialog;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;

public class DlgDownload extends DlgDownloadUI {
    private boolean dialogResult = false;

    public DlgDownload() {
        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(buttonDownload);

        urlField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                String filename = getFileNameFromURI(urlField.getText());

                nameField.setText(filename);
            }

            @Override
            public void removeUpdate(DocumentEvent e) {

            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                String filename = getFileNameFromURI(urlField.getText());

                nameField.setText(filename);
            }
        });

        //noinspection Convert2Lambda
        buttonPaste.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String data;
                try {
                    data = (String) Toolkit.getDefaultToolkit()
                            .getSystemClipboard().getData(DataFlavor.stringFlavor);
                } catch (UnsupportedFlavorException | IOException ex) {
                    return;
                }

                // Trim all begin and end white space
                urlField.setText(data.stripLeading().stripTrailing());
            }
        });

        buttonDownload.addActionListener(e -> onOK());

        buttonCancel.addActionListener(e -> onCancel());

        buttonBrowse.addActionListener(e -> onBrowse());

        // call onCancel() when cross is clicked
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });

        // call onCancel() on ESCAPE
        //noinspection Convert2Lambda
        contentPane.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

        String home = System.getProperty("user.home");

        pathField.setText(home + "\\Downloads");

        setTitle("Add New Download");
        setPreferredSize(new Dimension(400, 315));
        setResizable(false);
    }

    private void onOK() {
        dialogResult = true;

        File dlFile = Paths.get(getDirectory(), getFilename()).toFile();

        if (dlFile.exists()) {
            // Prompt it here
            int option = JOptionPane.showConfirmDialog(
                    contentPane, "File already exists overwrite?", "Confirm", JOptionPane.YES_NO_OPTION);

            if (option == JOptionPane.NO_OPTION)
                dialogResult = false;
        }

        if (dialogResult)
            dispose();
    }

    private void onCancel() {
        dispose();
    }

    private void onBrowse() {
        JFileChooser chooser = new JFileChooser(pathField.getText());

        chooser.setDialogTitle("Select Download Location");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        if (chooser.showDialog(this, "Select") == JFileChooser.APPROVE_OPTION) {
            pathField.setText(chooser.getSelectedFile().getPath());
        }
    }

    public boolean getResult() {
        return dialogResult;
    }

    public String getUrl() {
        return urlField.getText();
    }

    public String getFilename() {
        return nameField.getText();
    }

    public String getDirectory() {
        return pathField.getText();
    }

    public boolean getStartPaused() {
        return startDownloadPausedCheckBox.isSelected();
    }

    public boolean getVerify() {
        return useSHA1CheckBox.isSelected();
    }

    public String getSHA1() {
        return sha1Field.getText();
    }

    public static String getFileNameFromURI(String uriString) {
        try {
            URI uri = new URI(uriString);
            return Paths.get(uri.getPath()).getFileName().toString();
        } catch (URISyntaxException | InvalidPathException e) {
            return "";
        }
    }

    public static void main(String[] args) {
        DlgDownload dialog = new DlgDownload();
        dialog.pack();
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);
        System.exit(0);
    }
}
