# face_recognizer,py - A wrapper for face registration and recognition
import json
import base64
import face_recognition
from PIL import Image
import numpy as np
import os
import io
import glob
import pickle

g_known_face_encodings = []
g_known_face_names = []

CONST_KNOWN_FACE_ENCODINGS_PICKLE_FILE = "known_face_encodings.pkl"
CONST_KNOWN_FACE_LABELS_PICKLE_FILE = "known_face_labels.pkl"
CONST_LOCATION_MODEL = "hog"  # "hog"
CONST_ENCODING_MODEL = "small"  # "small"
CONST_NUM_JITTERS = 1
CONST_TOLERANCE = 0.6


def register_face(image_path):
    """Add a new image to the model"""
    return True


def search_face_with_raw_data(image_data):
    """
        Search the face with specified image data, must call load_known_images before calling this

        :param image_data: The image data in the form of numpy.ndarray
        :return: A json object representing the found faces (label, rect)
    """
    found_faces = []

    face_locations = face_recognition.face_locations(image_data, model=CONST_LOCATION_MODEL)
    face_encodings = face_recognition.face_encodings(face_image=image_data, known_face_locations=face_locations,
                                                     num_jitters=CONST_NUM_JITTERS, model=CONST_ENCODING_MODEL)

    # Loop through each face found in the unknown image
    for (top, right, bottom, left), face_encoding in zip(face_locations, face_encodings):
        # See if the face is a match for the known face(s)
        matches = face_recognition.compare_faces(known_face_encodings=g_known_face_encodings,
                                                 face_encoding_to_check=face_encoding, tolerance=CONST_TOLERANCE)
        name = "Unknown"

        # Or instead, use the known face with the smallest distance to the new face
        face_distances = face_recognition.face_distance(g_known_face_encodings, face_encoding)
        best_match_index = np.argmin(face_distances)
        if matches[best_match_index]:
            name = g_known_face_names[best_match_index]

        found_face = {
            "label": name,
            "rect": (top, right, bottom, left)
        }
        found_faces.append(found_face)

    return found_faces


def get_json_string_of_face_search_with_base64(image_base64):
    """
        Same as search_face_with_raw_data, with the result in a json object

        :param image_base64: The image data in the form of base64 encoding
        :return: A json object representing the found faces (label, rect)
    """
    base64_decoded = base64.b64decode(image_base64)
    image_decoded = Image.open(io.BytesIO(base64_decoded))
    image_np = np.array(image_decoded)
    search_found_faces = search_face_with_raw_data(image_np)

    output_json = {
        "found_faces": search_found_faces
    }
    return json.dumps(output_json)


def get_json_string_of_face_search_with_matrix(image_data):
    """
        Same as search_face_with_raw_data, with the result in a json object

        :param image_data: The image data in the form of numpy.ndarray
        :return: A json object representing the found faces (label, rect)
    """
    # [ { "label": "12345", "rec": (t, r, b, l) }, { "label": "abcdef", "rec": (t, r, b, l) }, ...]
    search_found_faces = search_face_with_raw_data(image_data)

    output_json = {
        "found_faces": search_found_faces
    }
    return json.dumps(output_json)


def search_face_with_path(image_path):
    """
        Search known user face in the existing model

        :param image_path: The image path
        :return: A json object representing the found faces (label, rect)
    """
    input_image_data = face_recognition.load_image_file(image_path)

    return search_face_with_raw_data(image_data=input_image_data)


def load_known_faces(images_path, pickle_file_path="."):
    """
        Load all registered faces and build the model

        :param images_path: The image path where all images to be registered are stored
        :param pickle_file_path: The path to which pickle file will be saved
        :return: None
    """
    global g_known_face_encodings
    global g_known_face_names

    pre_encodings_pickle_file = f"{pickle_file_path}/{CONST_KNOWN_FACE_ENCODINGS_PICKLE_FILE}"
    pre_labels_pickle_file_path = f"{pickle_file_path}/{CONST_KNOWN_FACE_LABELS_PICKLE_FILE}"
    if os.path.isfile(pre_encodings_pickle_file) and os.path.isfile(pre_labels_pickle_file_path):
        print("Found saved encodings and labels, load them now")
        with open(pre_encodings_pickle_file, 'rb') as known_encodings_pickle_in_file:
            g_known_face_encodings = pickle.load(known_encodings_pickle_in_file)

        with open(pre_labels_pickle_file_path, 'rb') as known_labels_pickle_in_file:
            g_known_face_names = pickle.load(known_labels_pickle_in_file)
        return True

    pattern = f"{images_path}/*.jpg"

    for image_path in glob.iglob(pattern, recursive=True):
        print(image_path)
        image_data = face_recognition.load_image_file(image_path)
        face_locations = face_recognition.face_locations(image_data, model=CONST_LOCATION_MODEL)
        all_face_encodings = face_recognition.face_encodings(face_image=image_data, known_face_locations=face_locations,
                                                             num_jitters=CONST_NUM_JITTERS, model=CONST_ENCODING_MODEL)

        if len(all_face_encodings) == 0:
            print(f"Error, cannot get encoding for the image: {image_path}")
            continue

        face_encoding = all_face_encodings[0]  # Assume this training image has only one face in each image
        g_known_face_encodings.append(face_encoding)
        slash_pos = image_path.rindex("/")
        dot_pos = image_path.rindex(".")
        label = image_path[slash_pos + 1:dot_pos]
        g_known_face_names.append(label)

    if len(g_known_face_encodings) == 0:
        print("No image file found in the data directory, please register the user face image first!")
        return False

    with open(pre_encodings_pickle_file, 'ab') as known_encodings_pickle_out_file:
        pickle.dump(g_known_face_encodings, known_encodings_pickle_out_file)

    with open(pre_labels_pickle_file_path, 'ab') as known_labels_pickle_out_file:
        pickle.dump(g_known_face_names, known_labels_pickle_out_file)

    print('Learned encoding for', len(g_known_face_encodings), 'images.')
    return True
