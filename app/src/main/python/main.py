from PIL import Image, ImageFont, ImageDraw
import face_recognition
import face_recognition_wrapper
import math


def run_app():
    # Convert the image to a PIL-format image so that we can draw on top of it with the Pillow library
    # See http://pillow.readthedocs.io/ for more about PIL/Pillow
    test_image_path = "images/test/test-3-persons.png"
    face_recognition_wrapper.load_known_faces(images_path="./images/train")
    unknown_image = face_recognition.load_image_file(test_image_path)

    found_faces = face_recognition_wrapper.search_face_with_path(test_image_path)

    pil_image = Image.fromarray(unknown_image)
    draw = ImageDraw.Draw(pil_image)

    for face_info in found_faces:
        # Draw a box around the face using the Pillow module
        (top, right, bottom, left) = face_info["rec"]
        draw.rectangle(((left, top), (right, bottom)), outline=(0, 0, 255))

        # Draw a label with a name below the face
        text_left, text_top, text_right, text_bottom = draw.textbbox((0, 0), face_info["label"])
        text_height = math.fabs(text_top - text_bottom)
        draw.rectangle(((left, bottom - text_height - 10), (right, bottom)), fill=(0, 0, 255), outline=(0, 0, 255))

        font = ImageFont.truetype("Courier.dfont", 20)
        draw.text((left + 6, bottom - text_height - 5), face_info["label"], fill=(255, 255, 255, 255), font=font)

    del draw

    # Display the resulting image
    pil_image.show()


if __name__ == "__main__":
    run_app()

