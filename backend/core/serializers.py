from rest_framework_simplejwt.serializers import TokenObtainPairSerializer
from rest_framework import serializers
from .models import User

class CustomTokenObtainPairSerializer(TokenObtainPairSerializer):
    """
    Custom Logic: When a user logs in, add their ROLE and ID to the response.
    """
    @classmethod
    def get_token(cls, user):
        token = super().get_token(user)
        # Add custom claims to the token (encrypted inside the JWT)
        token['role'] = user.role
        token['username'] = user.username
        return token

    def validate(self, attrs):
        # This method runs when the user POSTs their credentials
        data = super().validate(attrs)
        
        # Add extra data to the JSON response (visible to the App)
        data['role'] = self.user.role
        data['user_id'] = self.user.id
        data['full_name'] = self.user.username # Or fetch from StudentProfile if needed
        
        # Security Check: Is the user blocked?
        if self.user.is_blocked:
             raise serializers.ValidationError("This account has been blocked by Admin.")
             
        return data