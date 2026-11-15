package com.example.demo;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ImagemEventoService {

    public static final int MAX_IMAGENS = 5;
    public static final long TAMANHO_MAXIMO = 5L * 1024 * 1024;

    private static final Map<String, String> EXTENSOES_PERMITIDAS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/gif", "gif"
    );

    private final Path diretorio = Paths.get("uploads", "eventos").toAbsolutePath().normalize();

    public List<String> salvar(MultipartFile[] arquivos) throws IOException {
        List<MultipartFile> selecionados = new ArrayList<>();
        if (arquivos != null) {
            for (MultipartFile arquivo : arquivos) {
                if (arquivo != null && !arquivo.isEmpty()) {
                    selecionados.add(arquivo);
                }
            }
        }

        if (selecionados.size() > MAX_IMAGENS) {
            throw new IllegalArgumentException("Envie no máximo " + MAX_IMAGENS + " imagens por evento.");
        }

        List<ImagemPreparada> imagens = new ArrayList<>();
        for (MultipartFile arquivo : selecionados) {
            imagens.add(preparar(arquivo));
        }

        return gravar(imagens);
    }

    public ImagensEvento salvarEvento(MultipartFile capa, MultipartFile[] galeria,
                                      boolean exigirCapa) throws IOException {
        List<MultipartFile> fotosGaleria = new ArrayList<>();
        if (galeria != null) {
            for (MultipartFile arquivo : galeria) {
                if (arquivo != null && !arquivo.isEmpty()) {
                    fotosGaleria.add(arquivo);
                }
            }
        }
        if (fotosGaleria.size() > MAX_IMAGENS) {
            throw new IllegalArgumentException("Envie no máximo " + MAX_IMAGENS + " fotos na galeria.");
        }
        if (exigirCapa && (capa == null || capa.isEmpty())) {
            throw new IllegalArgumentException("Selecione uma foto de capa para o evento.");
        }

        List<ImagemPreparada> preparadas = new ArrayList<>();
        boolean temCapa = capa != null && !capa.isEmpty();
        if (temCapa) {
            preparadas.add(preparar(capa));
        }
        for (MultipartFile foto : fotosGaleria) {
            preparadas.add(preparar(foto));
        }

        List<String> caminhos = gravar(preparadas);
        String caminhoCapa = temCapa ? caminhos.get(0) : null;
        List<String> caminhosGaleria = caminhos.subList(temCapa ? 1 : 0, caminhos.size());
        return new ImagensEvento(caminhoCapa, List.copyOf(caminhosGaleria));
    }

    private List<String> gravar(List<ImagemPreparada> imagens) throws IOException {
        Files.createDirectories(diretorio);
        List<Path> gravados = new ArrayList<>();
        List<String> caminhos = new ArrayList<>();
        try {
            for (ImagemPreparada imagem : imagens) {
                String nomeArquivo = UUID.randomUUID() + "." + imagem.extensao();
                Path destino = diretorio.resolve(nomeArquivo);
                Files.write(destino, imagem.conteudo(), StandardOpenOption.CREATE_NEW);
                gravados.add(destino);
                caminhos.add("/uploads/eventos/" + nomeArquivo);
            }
            return caminhos;
        } catch (IOException | RuntimeException erro) {
            for (Path gravado : gravados) {
                Files.deleteIfExists(gravado);
            }
            throw erro;
        }
    }

    public Resource carregar(String nomeArquivo) throws IOException {
        if (nomeArquivo == null || !nomeArquivo.matches("[0-9a-fA-F-]{36}\\.(jpg|png|gif)")) {
            return null;
        }

        Path arquivo = diretorio.resolve(nomeArquivo).normalize();
        if (!arquivo.startsWith(diretorio) || !Files.isRegularFile(arquivo)) {
            return null;
        }

        return new UrlResource(arquivo.toUri());
    }

    public void remover(List<String> caminhos) throws IOException {
        for (String caminho : caminhos) {
            if (caminho == null || !caminho.startsWith("/uploads/eventos/")) {
                continue;
            }

            String nomeArquivo = caminho.substring("/uploads/eventos/".length());
            if (!nomeArquivo.matches("[0-9a-fA-F-]{36}\\.(jpg|png|gif)")) {
                continue;
            }

            Path arquivo = diretorio.resolve(nomeArquivo).normalize();
            if (arquivo.startsWith(diretorio)) {
                Files.deleteIfExists(arquivo);
            }
        }
    }

    private ImagemPreparada preparar(MultipartFile arquivo) throws IOException {
        if (arquivo.getSize() > TAMANHO_MAXIMO) {
            throw new IllegalArgumentException("Cada imagem deve ter no máximo 5 MB.");
        }

        String extensao = EXTENSOES_PERMITIDAS.get(arquivo.getContentType());
        if (extensao == null) {
            throw new IllegalArgumentException("Use imagens JPG, PNG ou GIF.");
        }

        byte[] conteudo = arquivo.getBytes();
        BufferedImage imagem = ImageIO.read(new ByteArrayInputStream(conteudo));
        if (imagem == null || imagem.getWidth() > 10000 || imagem.getHeight() > 10000) {
            throw new IllegalArgumentException("O arquivo enviado não é uma imagem válida ou excede as dimensões permitidas.");
        }

        return new ImagemPreparada(conteudo, extensao);
    }

    public record ImagensEvento(String capa, List<String> galeria) {}

    private record ImagemPreparada(byte[] conteudo, String extensao) {}
}