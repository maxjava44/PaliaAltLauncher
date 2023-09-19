package main;

import java.awt.EventQueue;

import javax.swing.JFrame;
import java.awt.BorderLayout;
import javax.swing.JButton;
import javax.swing.JScrollPane;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import javax.swing.JLabel;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.awt.event.ActionEvent;
import javax.swing.JTextPane;

public class Main2 {

	private JFrame frame;
	private static JTextPane lblNewLabel;
	JButton btnNewButton;

	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					Main2 window = new Main2();
					window.frame.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * Create the application.
	 */
	public Main2() {
		initialize();
	}

	/**
	 * Initialize the contents of the frame.
	 */
	private void initialize() {
		frame = new JFrame();
		frame.setBounds(100, 100, 450, 300);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.getContentPane().setLayout(new BorderLayout(0, 0));
		
		btnNewButton = new JButton("Update");
		btnNewButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				new Thread(new Runnable() {
					
					@Override
					public void run() {
						btnNewButton.setEnabled(false);
						// TODO Auto-generated method stub
						try {
							String manifestUrl = "https://update.palia.com/manifest/PatchManifest.json";
							download(Paths.get(".", "PatchManifest.json").toFile(), manifestUrl);
							String manifest = FileUtils.readFileToString(Paths.get(".", "PatchManifest.json").toFile());

							JSONObject manifestObj = new JSONObject(manifest);

							for (String key : manifestObj.keySet()) {
								JSONObject versionObj = manifestObj.getJSONObject(key);
								for (Object fileObj : versionObj.getJSONArray("Files")) {
									JSONObject fileJSONObj = (JSONObject) fileObj;
									printToLabel("Download " + fileJSONObj.getString("URL"));
									handleFile(fileJSONObj.getString("URL"), fileJSONObj.getString("Hash"));
								}
							}
						} catch (Exception e1) {
							// TODO Auto-generated catch block
							e1.printStackTrace();
						}
						System.exit(0);
					}
				}).start();
				
				
			}
		});
		frame.getContentPane().add(btnNewButton, BorderLayout.SOUTH);
		
		JScrollPane scrollPane = new JScrollPane();
		frame.getContentPane().add(scrollPane, BorderLayout.CENTER);
		
		lblNewLabel = new JTextPane();
		scrollPane.setViewportView(lblNewLabel);
	}
	
	public static void download(File toSave, String url) throws MalformedURLException, IOException {
		HttpURLConnection manifestConnection = (HttpURLConnection) new URL(url).openConnection();
		manifestConnection.addRequestProperty("User-Agent",
				"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15");
		ReadableByteChannel readChannel = Channels.newChannel(manifestConnection.getInputStream());
		FileOutputStream fileOutputStream = new FileOutputStream(toSave);
		FileChannel fileChannel = fileOutputStream.getChannel();
		fileChannel.transferFrom(readChannel, 0, Long.MAX_VALUE);
		fileOutputStream.close();
	}

	public static boolean downloadWithVerify(File toSave, String url, String hash) throws Exception {
		
		boolean retry = true;
		
		while(retry) {
			try {
				OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder();
				OkHttpClient client = clientBuilder.build();

				Request sizeRequest = new Request.Builder().method("HEAD", null).url(url).build();
				long existingSize = 0;
				if (toSave.exists()) {
					existingSize = FileUtils.sizeOf(toSave);
				} else {
					toSave.getParentFile().mkdirs();
				}
				long downloadSize = Long.parseLong(client.newCall(sizeRequest).execute().header("Content-Length"));

				FileOutputStream fileOutputStream;

				if (existingSize == downloadSize) {
					String actualHash;
					if(!hash.equals("")) {
						FileInputStream fileStream = new FileInputStream(toSave);
						MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
						byte[] buffer = new byte[2048];
						int read;
						do {
							read = fileStream.read(buffer);
							if (read > 0) {
								sha256.update(buffer, 0, read);
							}
						} while (read != -1);
						actualHash = new String(Hex.encodeHex(sha256.digest()));
						fileStream.close();
					} else {
						actualHash = "";
					}
					
					if (actualHash.equals(hash)) {
						return false;
					} else {
						existingSize = 0;
						fileOutputStream = new FileOutputStream(toSave);
					}
				} else {
					fileOutputStream = new FileOutputStream(toSave, true);
				}

				Request downloadRequest = new Request.Builder().url(url)
						.header("Range", "bytes=" + existingSize + "-" + downloadSize).build();

				try (Response response = client.newCall(downloadRequest).execute()) {
					try (InputStream responseStream = response.body().byteStream()) {
						long bytesWritten = existingSize;
						long lastPrint = existingSize;
						byte[] buffer = new byte[2048];
						int read;
						do {
							read = responseStream.read(buffer);
							if (read > 0) {
								fileOutputStream.write(buffer, 0, read);
								bytesWritten += read;
								if (bytesWritten - lastPrint > 1000000) {
									printToLabel(url + " has downloaded " + ((bytesWritten * 1.0)/downloadSize) * 100.0 + "%" + " of " + downloadSize / 1000000 + " MBs");
									lastPrint = bytesWritten;
								}
							}
						} while (read != -1);
						fileOutputStream.close();
					}
				}

				FileInputStream fileStream = new FileInputStream(toSave);
				MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
				byte[] buffer = new byte[2048];
				int read;
				do {
					read = fileStream.read(buffer);
					if (read > 0) {
						sha256.update(buffer, 0, read);
					}
				} while (read != -1);
				String actualHash = new String(Hex.encodeHex(sha256.digest()));
				fileStream.close();
				if (!actualHash.equals(hash) && !hash.equals("")) {
					throw new Exception("Die Datei " + toSave.toString() + "konnte nicht heruntergeladen werden");
				}
				retry = false;
			} catch (Exception e) {
				printToLabel("Error! Retrying...");
			}
			
		}
		return true;
		
	}
	
	public static void printToLabel(String text) {
		lblNewLabel.setText(text + System.lineSeparator() + lblNewLabel.getText());
	}

	public static void handleFile(String url, String hash) throws Exception {
		String folder = Paths.get("D:\\", "Palia").toFile().exists()
				? Paths.get("D:\\", "Palia").toAbsolutePath().toString()
				: Paths.get("C:\\", "Program Files", "Palia").toAbsolutePath().toString();

		String[] urlSplit = url.split("/");
		String fileName = urlSplit[urlSplit.length - 1];

		if (fileName.endsWith("pak")) {
			downloadWithVerify(new File(folder + File.separatorChar + "Palia" + File.separatorChar + "Content"
					+ File.separatorChar + "Paks" + File.separatorChar + fileName), url, hash);
		} else if (fileName.endsWith("exe")) {
			downloadWithVerify(new File(folder + File.separatorChar + "Palia" + File.separatorChar + "Binaries"
					+ File.separatorChar + "Win64" + File.separatorChar + fileName), url, hash);
		} else if (fileName.endsWith("zip")) {
			File theZip = new File(folder + File.separatorChar + fileName);
			if(downloadWithVerify(theZip, url, hash)) {
				ZipFileCompressUtils zipFileCompressUtils = new ZipFileCompressUtils();

				zipFileCompressUtils.extractZip(folder + File.separatorChar + fileName, folder);
			}			
		}
	}

}
