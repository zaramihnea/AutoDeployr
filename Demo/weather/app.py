import requests
from dotenv import load_dotenv


@app.route('/getWeather', methods=['GET'])
def get_weather():
    city = request.args.get('city', 'London')
    WEATHER_API_KEY = os.getenv('WEATHER_API_KEY')
    WEATHER_API_URL = "http://api.openweathermap.org/data/2.5/weather"
    
    params = {
            'q': city,
            'appid': WEATHER_API_KEY,
            'units': 'metric'
        }
        
    response = requests.get(WEATHER_API_URL, params=params)
    
    weather_data = response.json()
        
    result = {
        'city': weather_data['name'],
        'country': weather_data['sys']['country'],
        'temperature': weather_data['main']['temp'],
        'feels_like': weather_data['main']['feels_like'],
        'humidity': weather_data['main']['humidity'],
        'pressure': weather_data['main']['pressure'],
        'description': weather_data['weather'][0]['description'],
        'weather_main': weather_data['weather'][0]['main'],
        'wind_speed': weather_data['wind']['speed'],
        'visibility': weather_data.get('visibility', 'N/A'),
        'timestamp': weather_data['dt']
        }
        
    return jsonify({
        'success': True,
        'data': result
    }), 200