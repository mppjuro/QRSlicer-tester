package org.example;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Arrays;

@SpringBootApplication
public class ImageSenderApplication implements CommandLineRunner {

    // Nazwy 12 odprowadzeń EKG (opcjonalnie do opisu plików)
    private static final String[] LEAD_NAMES = {
            "I", "II", "III",
            "aVR", "aVL", "aVF",
            "V1", "V2", "V3", "V4", "V5", "V6"
    };

    public static void main(String[] args) {
        SpringApplication.run(ImageSenderApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        // 1. Adres serwera (endpoint)
        String serverUrl = "http://localhost:9090/process";
        // Zmień, jeśli Twój serwer nasłuchuje na innym porcie/ścieżce

        // 2. Plik .png do wysłania
        String path = "src/main/java/org/example/test2.png"; // Zmień w razie potrzeby
        File file = new File(path);
        if (!file.exists()) {
            System.err.println("Brak pliku: " + file.getAbsolutePath());
            return;
        }

        // 3. Wczytanie do byte[]
        byte[] imageBytes = loadFile(file);
        if (imageBytes == null || imageBytes.length == 0) {
            System.err.println("Nie udało się odczytać pliku: " + path);
            return;
        }

        // 4. Tworzymy obiekt requestu z polem fileBytes (typu Byte[])
        FileRequest req = new FileRequest();
        req.pngBytes = new Byte[imageBytes.length];
        for (int i = 0; i < imageBytes.length; i++) {
            req.pngBytes[i] = imageBytes[i]; // autoboxing (byte -> Byte)
        }

        // 5. Wysyłamy do /process (Spring użyje Jacksona do serializacji 'req' w JSON)
        RestTemplate restTemplate = new RestTemplate();
        // Odbieramy tablicę CompressedBitmap (zdeserializowaną przez Jackson)
        CompressedBitmap[] responseArr = restTemplate.postForObject(
                serverUrl,
                req,
                CompressedBitmap[].class
        );

        if (responseArr == null) {
            System.err.println("Otrzymano pustą odpowiedź z serwera!");
            return;
        }
        System.out.println("Otrzymano odpowiedź z serwera. Liczba sub-obszarów: " + responseArr.length);

        // 6. Przetwarzamy każdy CompressedBitmap -> boolean[][] -> zapis .png i .txt
        for (int i = 0; i < responseArr.length; i++) {
            CompressedBitmap cb = responseArr[i];
            boolean[][] matrix = decompressToBooleanMatrix(cb.width, cb.height, cb.data);

            // Nazwy plików
            String leadName = (i < LEAD_NAMES.length) ? LEAD_NAMES[i] : ("lead_" + (i+1));
            String pngFile = "output_" + leadName + ".png";
            String txtFile = "output_" + leadName + ".txt";

            saveToPng(matrix, pngFile);
            saveToTxt(matrix, txtFile);

            System.out.println("Zapisano: " + pngFile + ", " + txtFile);
        }

        System.out.println("Wykonano wysyłanie pliku i zapis sub-obszarów.");
    }

    /**
     * Wczytuje plik do tablicy bajtów.
     */
    private byte[] loadFile(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            int read = fis.read(data);
            if (read != data.length) {
                return null;
            }
            return data;
        }
    }

    /**
     * Dekompresja int[] -> boolean[][] (32 bity na int).
     */
    private boolean[][] decompressToBooleanMatrix(int width, int height, int[] data) {
        boolean[][] matrix = new boolean[height][width];

        int bitIndex = 0;
        int intIndex = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (bitIndex == 32) {
                    bitIndex = 0;
                    intIndex++;
                }
                int currentInt = data[intIndex];
                boolean isTrue = ((currentInt >>> bitIndex) & 1) == 1;
                matrix[y][x] = isTrue;

                bitIndex++;
            }
        }
        return matrix;
    }

    /**
     * Zapis boolean[][] do .png (true->czarny, false->biały).
     */
    private void saveToPng(boolean[][] matrix, String fileName) throws IOException {
        int h = matrix.length;
        int w = matrix[0].length;

        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                image.setRGB(x, y, matrix[y][x] ? Color.BLACK.getRGB() : Color.WHITE.getRGB());
            }
        }
        ImageIO.write(image, "png", new File(fileName));
    }

    /**
     * Zapis boolean[][] do pliku .txt w postaci ciągów 0/1 dla każdego wiersza.
     */
    private void saveToTxt(boolean[][] matrix, String fileName) throws IOException {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(fileName))) {
            for (boolean[] row : matrix) {
                StringBuilder sb = new StringBuilder(row.length);
                for (boolean b : row) {
                    sb.append(b ? '1' : '0');
                }
                bw.write(sb.toString());
                bw.newLine();
            }
        }
    }
}
