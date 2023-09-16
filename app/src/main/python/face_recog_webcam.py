import face_recognition_wrapper
import cv2
import numpy as np

# This is a demo of running face recognition on live video from your webcam. It's a little more complicated than the
# other example, but it includes some basic performance tweaks to make things run a lot faster:
#   1. Process each video frame at 1/4 resolution (though still display it at full resolution)
#   2. Only detect faces in every other frame of video.

# PLEASE NOTE: This example requires OpenCV (the `cv2` library) to be installed only to read from your webcam.
# OpenCV is *not* required to use the face_recognition library. It's only required if you want to run this
# specific demo. If you have trouble installing it, try any of the other demos that don't require it instead.


def run_app():
    """Run the app"""
    face_recognition_wrapper.load_known_faces(images_path="./images/train")
    # Get a reference to webcam #0 (the default one)
    video_capture = cv2.VideoCapture(0)

    face_locations = []
    face_encodings = []
    face_names = []
    process_this_frame = True

    found_faces = []
    while True:
        ret, frame = video_capture.read()
        found_faces.clear()

        # Only process every other frame of video to save time
        if process_this_frame:
            # Resize frame of video to 1/4 size for faster face recognition processing
            small_frame = cv2.resize(frame, (0, 0), fx=0.25, fy=0.25)

            # Convert the image from BGR color (which OpenCV uses) to RGB color (which face_recognition uses)
            # rgb_small_frame = small_frame[:, :, ::-1]
            rgb_small_frame = np.ascontiguousarray(small_frame[:, :, ::-1])
            found_faces = face_recognition_wrapper.search_face_with_raw_data(rgb_small_frame)

        process_this_frame = not process_this_frame

        # Display the results
        # for (top, right, bottom, left), name in zip(face_locations, face_names):
        for face_info in found_faces:
            # Scale back up face locations since the frame we detected in was scaled to 1/4 size
            (top, right, bottom, left) = face_info["rec"]

            top *= 4
            right *= 4
            bottom *= 4
            left *= 4

            # Draw a box around the face
            cv2.rectangle(frame, (left, top), (right, bottom), (0, 0, 255), 2)

            # Draw a label with a name below the face
            cv2.rectangle(frame, (left, bottom - 35), (right, bottom), (0, 0, 255), cv2.FILLED)
            font = cv2.FONT_HERSHEY_DUPLEX
            cv2.putText(frame, face_info["label"], (left + 6, bottom - 6), font, 1.0, (255, 255, 255), 1)

        # Display the resulting image
        cv2.imshow('Video', frame)

        # Hit 'q' on the keyboard to quit!
        if cv2.waitKey(1) & 0xFF == ord('q'):
            break

    # Release handle to the webcam
    video_capture.release()
    cv2.destroyAllWindows()


if __name__ == "__main__":
    run_app()
