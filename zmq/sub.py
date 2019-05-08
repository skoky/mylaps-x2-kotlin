# listens on `passing` messages
# must run with python 2.x
import zmq

context = zmq.Context()
socket = context.socket(zmq.SUB)
socket.setsockopt(zmq.SUBSCRIBE, "")

print("Collecting updates from MyLaps X2 server...")
socket.connect("tcp://127.0.0.1:5556")

while True:
    print(socket.recv_json())
