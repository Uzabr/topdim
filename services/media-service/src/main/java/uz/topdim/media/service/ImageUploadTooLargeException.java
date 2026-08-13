package uz.topdim.media.service;

public class ImageUploadTooLargeException extends InvalidImageUploadException {

    public ImageUploadTooLargeException(String message) {
        super(message);
    }
}
