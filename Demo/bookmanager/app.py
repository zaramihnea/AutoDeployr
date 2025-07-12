from flask import Flask, request, jsonify
import os
from dotenv import load_dotenv
import psycopg2
from psycopg2.extras import RealDictCursor
import hashlib
import google.generativeai as genai

# Load environment variables
load_dotenv()

# Database connection
DATABASE_URL = os.getenv('DATABASE_URL')

if DATABASE_URL:
    conn = psycopg2.connect(DATABASE_URL, cursor_factory=RealDictCursor)
else:
    DB_HOST = os.getenv('DB_HOST')
    DB_PORT = os.getenv('DB_PORT', '5432')
    DB_NAME = os.getenv('DB_NAME', 'postgres')
    DB_USER = os.getenv('DB_USER', 'postgres')
    DB_PASSWORD = os.getenv('DB_PASSWORD')

    conn = psycopg2.connect(
        host=DB_HOST,
        port=DB_PORT,
        dbname=DB_NAME,
        user=DB_USER,
        password=DB_PASSWORD,
        cursor_factory=RealDictCursor
    )

# Configure Gemini API
genai.configure(api_key=os.getenv('GEMINI_API_KEY'))

app = Flask(__name__)

def hash_password(password):
    return hashlib.sha256(password.encode()).hexdigest()

@app.route('/signup', methods=['POST'])
def signup():
    data = request.get_json()
    if not data or not all(k in data for k in ('name', 'email', 'password')):
        return jsonify({'error': 'Name, email and password required'}), 400
    
    name = data['name']
    email = data['email']
    password = hash_password(data['password'])
    
    cur = conn.cursor()
    try:
        cur.execute(
            "INSERT INTO users (name, email, password) VALUES (%s, %s, %s) RETURNING id;",
            (name, email, password)
        )
        user_id = cur.fetchone()['id']
        conn.commit()
        return jsonify({'id': user_id, 'message': 'User created successfully'}), 201
    except psycopg2.IntegrityError:
        conn.rollback()
        return jsonify({'error': 'Email already exists'}), 400
    except Exception as e:
        conn.rollback()
        return jsonify({'error': str(e)}), 500
    finally:
        cur.close()

@app.route('/getAllBooks', methods=['GET'])
def get_all_books():
    cur = conn.cursor()
    try:
        cur.execute("SELECT b.id, b.title, u.name as author FROM books b JOIN users u ON b.user_id = u.id;")
        books = cur.fetchall()
        return jsonify({'books': books}), 200
    except Exception as e:
        return jsonify({'error': str(e)}), 500
    finally:
        cur.close()

@app.route('/getUserBooks', methods=['POST'])
def get_user_books():
    data = request.get_json()
    if not data or 'user_id' not in data:
        return jsonify({'error': 'User ID required'}), 400
    
    user_id = data['user_id']
    cur = conn.cursor()
    try:
        cur.execute("SELECT id, title FROM books WHERE user_id = %s;", (user_id,))
        books = cur.fetchall()
        return jsonify({'books': books}), 200
    except Exception as e:
        return jsonify({'error': str(e)}), 500
    finally:
        cur.close()

@app.route('/addBook', methods=['POST'])
def add_book():
    data = request.get_json()
    if not data or not all(k in data for k in ('user_id', 'title')):
        return jsonify({'error': 'User ID and title required'}), 400
    
    user_id = data['user_id']
    title = data['title']
    
    cur = conn.cursor()
    try:
        cur.execute(
            "INSERT INTO books (user_id, title) VALUES (%s, %s) RETURNING id;",
            (user_id, title)
        )
        book_id = cur.fetchone()['id']
        conn.commit()
        return jsonify({'id': book_id, 'message': 'Book added successfully'}), 201
    except Exception as e:
        conn.rollback()
        return jsonify({'error': str(e)}), 500
    finally:
        cur.close()

@app.route('/deleteBook', methods=['POST'])
def delete_book():
    data = request.get_json()
    if not data or 'book_id' not in data:
        return jsonify({'error': 'Book ID required'}), 400
    
    book_id = data['book_id']
    cur = conn.cursor()
    try:
        cur.execute("DELETE FROM books WHERE id = %s;", (book_id,))
        if cur.rowcount == 0:
            return jsonify({'error': 'Book not found'}), 404
        conn.commit()
        return jsonify({'message': 'Book deleted successfully'}), 200
    except Exception as e:
        conn.rollback()
        return jsonify({'error': str(e)}), 500
    finally:
        cur.close()

@app.route('/estimateBookPrice', methods=['POST'])
def estimate_book_price():
    data = request.get_json()
    if not data or 'book_id' not in data:
        return jsonify({'error': 'Book ID required'}), 400
    
    book_id = data['book_id']
    cur = conn.cursor()
    try:
        cur.execute("SELECT title FROM books WHERE id = %s;", (book_id,))
        result = cur.fetchone()
        if not result:
            return jsonify({'error': 'Book not found'}), 404
        
        book_title = result['title']
        
        # Use Gemini to estimate price
        model = genai.GenerativeModel('gemini-2.0-flash')
        prompt = f"What is the estimated price range for the book '{book_title}'? Please provide a brief response with just the price range."
        response = model.generate_content(prompt)
        
        return jsonify({
            'book_title': book_title,
            'estimated_price': response.text
        }), 200
    except Exception as e:
        return jsonify({'error': str(e)}), 500
    finally:
        cur.close()

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5500, debug=True) 